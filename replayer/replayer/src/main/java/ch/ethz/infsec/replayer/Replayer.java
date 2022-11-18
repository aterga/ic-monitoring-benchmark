package ch.ethz.infsec.replayer;

import ch.ethz.infsec.kafka.MonitorKafkaConfig;
import ch.ethz.infsec.monitor.Fact;
import ch.ethz.infsec.trace.formatter.*;
import ch.ethz.infsec.trace.parser.Crv2014CsvParser;
import ch.ethz.infsec.trace.parser.DejavuTraceParser;
import ch.ethz.infsec.trace.parser.MonpolyTraceParser;
import ch.ethz.infsec.trace.parser.TraceParser;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Replayer {
    private static final int FACT_CHUNK_SIZE = 128;

    private double timeMultiplier = 1.0;
    private String commandPrefix = ">";
    private long timestampInterval = -1;
    private String timestampPrefix = "###";
    private int queueCapacity = 1024;
    private boolean explicitEmissiontime = false;

    private Reporter reporter = new NullReporter();


    private class ReplayerWorker implements Runnable {
        private TraceParser parser;
        private TraceFormatter formatter;
        private BufferedReader input;
        private Output output;
        private LinkedBlockingQueue<ArrayList<OutputItem>> queue;
        private Thread inputThread;
        private boolean printEOF;

        ReplayerWorker(BufferedReader input, Output output, TraceParser parser, TraceFormatter formatter, boolean printEOF) {
            assert input != null && output != null && parser != null && formatter != null;
            this.printEOF = printEOF;
            this.input = input;
            this.output = output;
            this.parser = parser;
            this.formatter = formatter;
        }

        @Override
        public void run() {
            queue = new LinkedBlockingQueue<>(queueCapacity);

            Thread reporterThread = new Thread(reporter);
            reporterThread.setDaemon(true);
            reporterThread.start();

            InputWorker inputWorker = new InputWorker();
            inputThread = new Thread(inputWorker);
            inputThread.start();

            OutputWorker outputWorker = new OutputWorker(printEOF);
            Thread outputThread = new Thread(outputWorker);
            outputThread.start();

            try {
                inputThread.join();
                if (!inputWorker.isSuccessful()) {
                    outputThread.interrupt();
                }
                outputThread.join();
                reporterThread.join(2000);
            } catch (InterruptedException ignored) {
            }

            if (!inputWorker.isSuccessful() || !outputWorker.isSuccessful()) {
                System.exit(1);
            }
        }

        private class InputWorker implements Runnable {
            private boolean successful = false;
            private long currEmissionTime = -1;
            private long firstTimestamp = -1;

            private final ArrayList<OutputItem> parsedItems = new ArrayList<>();
            private ArrayList<OutputItem> currentChunk = new ArrayList<>(FACT_CHUNK_SIZE);

            private void putItem(OutputItem item, boolean force) throws InterruptedException {
                currentChunk.add(item);
                if (currentChunk.size() >= FACT_CHUNK_SIZE || force) {
                    queue.put(currentChunk);
                    currentChunk = new ArrayList<>(FACT_CHUNK_SIZE);
                }
            }

            private void emitParsedItems() throws InterruptedException {
                for (OutputItem item : parsedItems) {
                    putItem(item, false);
                }
                parsedItems.clear();
            }

            private long calculateEmissionTime(long timestamp) {
                long emissionTime;
                if (timeMultiplier > 0.0) {
                    emissionTime = Math.round((double) (timestamp - firstTimestamp) / timeMultiplier * 1000.0);
                } else {
                    emissionTime = 0;
                }
                return emissionTime;
            }

            private void processFact(Fact fact) {
                final long timestamp = fact.getTimestamp();
                if (firstTimestamp < 0) {
                    firstTimestamp = timestamp;
                }
                parsedItems.add(new FactItem(calculateEmissionTime(timestamp), fact));
            }

            private void processFactExplicitEmissiontime(Fact fact) {
                assert currEmissionTime != -1;
                parsedItems.add(new FactItem(calculateEmissionTime(currEmissionTime), fact));
            }

            public void run() {
                if (explicitEmissiontime)
                     firstTimestamp = 0;
                try {
                    String line;
                    while ((line = input.readLine()) != null) {
                        if (explicitEmissiontime) {
                            String[] parts = line.split("'");
                            assert parts.length == 2;
                            currEmissionTime = Long.parseLong(parts[0]);
                            line = parts[1];
                        }
                        if (line.startsWith(commandPrefix)) {
                            CommandItem commandItem = new CommandItem(calculateEmissionTime(currEmissionTime), line);
                            putItem(commandItem, false);
                        } else {
                            if (explicitEmissiontime)
                                parser.parseLine(this::processFactExplicitEmissiontime, line);
                            else
                                parser.parseLine(this::processFact, line);
                            emitParsedItems();
                        }
                    }
                    parser.endOfInput(this::processFact);
                    emitParsedItems();
                    putItem(new TerminalItem(), true);
                    successful = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            boolean isSuccessful() {
                return successful;
            }
        }

        private class OutputWorker implements Runnable {
            private boolean successful = false;

            private long startTimeMillis;
            private long startTimeNanos;
            private long tsIdx = 0;
            private boolean printEOF;

            OutputWorker(boolean printEOF) {
                this.printEOF = printEOF;
            }

            private void delay(long emissionTime) throws InterruptedException {
                long now = System.nanoTime();
                long elapsedMillis = (now - startTimeNanos) / 1_000_000L;
                long waitMillis = emissionTime - elapsedMillis;
                if (waitMillis > 1L) {
                    Thread.sleep(waitMillis);
                }
            }

            private void emitTimestamp(long relativeTimestamp) throws IOException {
                final long timestamp = startTimeMillis + relativeTimestamp;
                output.writeString(String.format(">LATENCY %d %d <\n", tsIdx, timestamp));
                output.flush();
                tsIdx++;
            }

            private void runInternal() throws InterruptedException, IOException {
                long nextTimestampToEmit = timestampInterval + timestampInterval / 2;
                long lastOutputTime = 0;

                Iterator<OutputItem> outputItems = queue.take().iterator();
                OutputItem outputItem = outputItems.next();
                startTimeMillis = System.currentTimeMillis();
                startTimeNanos = System.nanoTime();

                while (!(outputItem instanceof TerminalItem)) {
                    if (timestampInterval > 0) {
                        while (nextTimestampToEmit <= outputItem.emissionTime) {
                            delay(nextTimestampToEmit);
                            emitTimestamp(nextTimestampToEmit);
                            nextTimestampToEmit += timestampInterval;
                        }
                    }

                    if (outputItem.emissionTime > lastOutputTime)
                        lastOutputTime = outputItem.emissionTime;

                    delay(outputItem.emissionTime);
                    outputItem.emit(output, formatter);
                    outputItem.reportDelivery(reporter, startTimeNanos);


                    if (!outputItems.hasNext()) {
                        ArrayList<OutputItem> chunk = queue.poll();
                        if (chunk == null) {
                            reporter.reportUnderrun();
                            chunk = queue.take();
                        }
                        outputItems = chunk.iterator();
                    }
                    outputItem = outputItems.next();
                }

                if (timestampInterval > 0) {
                    emitTimestamp(lastOutputTime);
                }
                if (printEOF) {
                    output.writeString(">EOF<\n");
                    output.writeString(">TERMSTREAM<\n");
                    output.flush();
                }
                reporter.reportEnd();

                successful = true;
            }

            public void run() {
                try {
                    runInternal();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!successful) {
                    inputThread.interrupt();
                }
            }

            boolean isSuccessful() {
                return successful;
            }
        }

    }

    private static abstract class OutputItem {
        final long emissionTime;

        OutputItem(long emissionTime) {
            this.emissionTime = emissionTime;
        }

        abstract void emit(Output output, TraceFormatter formatter) throws IOException;

        abstract void reportDelivery(Reporter reporter, long startTime);
    }

    private static final class TerminalItem extends OutputItem {
        TerminalItem() {
            super(-1);
        }

        @Override
        void emit(Output output, TraceFormatter formatter) {
            throw new UnsupportedOperationException();
        }

        @Override
        void reportDelivery(Reporter reporter, long startTime) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FactItem extends OutputItem {
        final Fact fact;

        FactItem(long emissionTime, Fact fact) {
            super(emissionTime);
            this.fact = fact;
        }

        @Override
        void emit(Output output, TraceFormatter formatter) throws IOException {
            output.writeFact(fact, formatter);
            if (fact.isTerminator()) {
                output.flush();
            }
        }

        @Override
        void reportDelivery(Reporter reporter, long startTime) {
            reporter.reportDelivery(this, startTime);
        }
    }

    private static final class CommandItem extends OutputItem {
        final String command;

        CommandItem(long emissionTime, String command) {
            super(emissionTime);
            this.command = command;
        }

        @Override
        public void emit(Output output, TraceFormatter formatter) throws IOException {
            output.writeString(command + "\n");
            output.flush();
        }

        @Override
        void reportDelivery(Reporter reporter, long startTime) {
        }
    }

    private abstract class Output {
        abstract void writeString(String string) throws IOException;

        void writeFact(Fact fact, TraceFormatter formatter) throws IOException {
            formatter.printFact(this::writeString, fact);
        }

        abstract void flush() throws IOException;
    }

    private class KafkaOutput extends Output {
        private KafkaProducer<String, String> producer;
        private int partition;
        private String topic;

        KafkaOutput(int partition, KafkaProducer<String, String> producer) {
            this.producer = producer;
            topic = MonitorKafkaConfig.getTopic();
            this.partition = partition;
        }

        @Override
        void writeString(String string) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, partition, "", string);
            producer.send(record);
        }

        @Override
        void flush() {
            producer.flush();
        }
    }

    private class StandardOutput extends Output {
        private final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));

        @Override
        void writeString(String string) throws IOException {
            writer.write(string);
        }

        @Override
        void flush() throws IOException {
            writer.flush();
        }
    }

    private class SocketOutput extends Output {
        private Socket clientSocket;
        private BufferedWriter writer;

        SocketOutput(Socket clientSocket) throws IOException {
            this.clientSocket = clientSocket;
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            System.err.printf("Client connected: %s:%d\n",
                    clientSocket.getInetAddress().getHostAddress(),
                    clientSocket.getPort());
        }

        private void closeClient() {
            if (clientSocket == null) {
                return;
            }
            try {
                writer.close();
                clientSocket.close();
            } catch (IOException ignored) {
            }
            writer = null;
            clientSocket = null;
        }

        private void handleError(IOException e) throws IOException {
            System.err.println("Could not write to client");
            closeClient();
            throw e;
        }

        @Override
        void writeString(String string) throws IOException {
            boolean tryAgain = true;
            do {
                try {
                    writer.write(string);
                    tryAgain = false;
                } catch (IOException e) {
                    handleError(e);
                }
            } while (tryAgain);
        }

        @Override
        void flush() throws IOException {
            boolean tryAgain = true;
            do {
                try {
                    writer.flush();
                    tryAgain = false;
                } catch (IOException e) {
                    handleError(e);
                }
            } while (tryAgain);
        }
    }

    private interface Reporter extends Runnable {
        void reportUnderrun();

        void reportDelivery(FactItem item, long startTime);

        void reportEnd();
    }

    private static class NullReporter implements Reporter {
        @Override
        public void reportUnderrun() {
        }

        @Override
        public void reportDelivery(FactItem item, long startTime) {
        }

        @Override
        public void reportEnd() {
        }

        @Override
        public void run() {
        }
    }

    private static class IntervalReporter implements Reporter {
        static final long INTERVAL_MILLIS = 1000L;

        private final boolean verbose;

        private volatile boolean running = true;
        private long startTime;
        private long lastReport;

        private int underruns = 0;
        private int eventsInCurrentIndex = 0;
        private int indices = 0;
        private int indicesSinceLastReport = 0;
        private int totalEvents = 0;
        private int eventsSinceLastReport = 0;
        private long currentDelay = 0;
        private long delaySum = 0;
        private long maxDelay = 0;
        private long maxDelaySinceLastReport = 0;

        IntervalReporter(boolean verbose) {
            this.verbose = verbose;
        }

        @Override
        public synchronized void reportUnderrun() {
            ++underruns;
        }

        @Override
        public synchronized void reportDelivery(FactItem item, long startTime) {
            if (item.fact.isTerminator()) {
                long now = System.nanoTime();

                ++indices;
                ++indicesSinceLastReport;

                totalEvents += eventsInCurrentIndex;
                eventsSinceLastReport += eventsInCurrentIndex;

                long elapsedMillis = (now - startTime) / 1_000_000L;
                currentDelay = Math.max(0, elapsedMillis - item.emissionTime);
                delaySum += currentDelay;
                maxDelay = Math.max(maxDelay, currentDelay);
                maxDelaySinceLastReport = Math.max(maxDelaySinceLastReport, currentDelay);

                eventsInCurrentIndex = 0;
            } else {
                ++eventsInCurrentIndex;
            }
        }

        @Override
        public synchronized void reportEnd() {
            running = false;
        }

        private synchronized void doReport() {
            long now = System.nanoTime();

            double totalSeconds = (double) (now - startTime) / 1e9;
            double deltaSeconds = (double) (now - lastReport) / 1e9;

            double indexRate = (double) indicesSinceLastReport / deltaSeconds;
            double eventRate = (double) eventsSinceLastReport / deltaSeconds;
            double delaySeconds = (double) currentDelay / 1000.0;
            double totalAverageDelaySeconds = indices > 0 ? (double) delaySum / ((double) indices * 1000.0) : 0;
            double maxDelaySeconds = (double) maxDelay / 1000.0;
            double currentMaxDelaySeconds = (double) maxDelaySinceLastReport / 1000.0;

            if (verbose) {
                System.err.printf(
                        "%5.1fs: %8.1f indices/s, %8.1f events/s, %6.3fs delay, %6.3fs peak delay, %6.3fs max. delay, %6.3fs avg. delay, %9d indices, %9d events, %6d underruns\n",
                        totalSeconds, indexRate, eventRate, delaySeconds, currentMaxDelaySeconds, maxDelaySeconds, totalAverageDelaySeconds, indices, totalEvents, underruns);
            } else {
                System.err.printf("%5.1f   %8.1f %8.1f   %6.3f %6.3f %6.3f %6.3f\n",
                        totalSeconds, indexRate, eventRate, delaySeconds, currentMaxDelaySeconds, maxDelaySeconds, totalAverageDelaySeconds);
            }

            indicesSinceLastReport = 0;
            eventsSinceLastReport = 0;
            currentDelay = 0;
            maxDelaySinceLastReport = 0;

            lastReport = now;
        }

        @Override
        public void run() {
            try {
                long schedule;
                synchronized (this) {
                    startTime = System.nanoTime();
                    lastReport = startTime;
                    schedule = startTime;
                }

                while (running) {
                    schedule += INTERVAL_MILLIS * 1_000_000L;
                    long now = System.nanoTime();
                    long waitMillis = Math.max(0L, (schedule - now) / 1_000_000L);
                    Thread.sleep(waitMillis);
                    doReport();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class PercentileReporter implements Reporter {
        private final long hardLimit;
        private volatile boolean running = true;
        private final ArrayBlockingQueue<long[]> reuseSamples = new ArrayBlockingQueue<>(32);
        private final ArrayBlockingQueue<long[]> readySamples = new ArrayBlockingQueue<>(16);

        // Structure of samples array: {emission period [s], underruns, num. samples, latencies ...}
        private static final int PERIOD = 0;
        private static final int UNDERRUNS = 1;
        private static final int NUM_SAMPLES = 2;
        private static final int LATENCIES = 3;
        private long[] samples = new long[1024];

        PercentileReporter(long hardLimitSeconds) {
            this.hardLimit = hardLimitSeconds * 1_000L;
            for (int i = 0; i < readySamples.remainingCapacity(); ++i) {
                reuseSamples.add(new long[1024]);
            }
        }

        @Override
        public void reportUnderrun() {
            ++samples[UNDERRUNS];
        }

        @Override
        public void reportDelivery(FactItem item, long startTime) {
            if (item.fact.isTerminator()) {
                long now = System.nanoTime();
                long elapsedMillis = (now - startTime) / 1_000_000L;
                long currentDelay = Math.max(0, elapsedMillis - item.emissionTime);
                if (hardLimit >= 0 && currentDelay > hardLimit) {
                    System.err.println("Hard latency limit reached");
                    System.exit(10);
                }

                long emissionPeriod = item.emissionTime / 1_000L;
                if (emissionPeriod != samples[PERIOD]) {
                    readySamples.add(samples);
                    samples = reuseSamples.poll();
                    if (samples == null) {
                        throw new RuntimeException("Reporting buffer underflow");
                    }
                    samples[PERIOD] = emissionPeriod;
                    samples[UNDERRUNS] = 0;
                    samples[NUM_SAMPLES] = 0;
                }
                if (LATENCIES + samples[NUM_SAMPLES] >= samples.length) {
                    long[] oldSamples = samples;
                    samples = new long[2 * oldSamples.length];
                    System.arraycopy(oldSamples, 0, samples, 0, oldSamples.length);
                }
                samples[LATENCIES + (int)samples[NUM_SAMPLES]] = currentDelay;
                ++samples[NUM_SAMPLES];
            }
        }

        @Override
        public void reportEnd() {
            running = false;
            readySamples.add(samples);
        }

        private static void doReport(long[] samples) {
            int numSamples = (int)samples[NUM_SAMPLES];
            long lat50 = 0, lat90 = 0, lat99 = 0, lat100 = 0;
            if (numSamples > 0) {
                Arrays.sort(samples, LATENCIES, LATENCIES + numSamples);
                lat50 = samples[LATENCIES + (int)(0.50 * numSamples)];
                lat90 = samples[LATENCIES + (int)(0.90 * numSamples)];
                lat99 = samples[LATENCIES + (int)(0.99 * numSamples)];
                lat100 = samples[LATENCIES + (numSamples - 1)];
            }
            System.err.printf("%6d, %7d, %6d, %6d, %6d, %6d, %9d\n", samples[PERIOD], numSamples,
                    lat50, lat90, lat99, lat100, samples[UNDERRUNS]);
        }

        @Override
        public void run() {
            System.err.println("  time, samples,    50%,    90%,    99%,    max, underruns");
            try {
                long lastReport = -1;
                while (running) {
                    long[] ready = readySamples.take();
                    long thisReport = ready[PERIOD];
                    for (long gap = lastReport + 1; gap < thisReport; ++gap) {
                        doReport(new long[] {gap, 0, 0});
                    }
                    doReport(ready);
                    reuseSamples.add(ready);
                    lastReport = thisReport;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void printHelp() {
        try {
            final ClassLoader classLoader = Replayer.class.getClassLoader();
            System.out.print(IOUtils.toString(Objects.requireNonNull(classLoader.getResource("README.txt")),
                    StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void invalidArgument() {
        System.err.println("Error: Invalid argument (see --help for usage).");
        System.exit(1);
    }

    private static TraceParser getTraceParser(String format, TraceParser.TerminatorMode mode) {
        TraceParser parser;
        switch (format) {
            case "csv":
                parser = new Crv2014CsvParser(null); break;
            case "monpoly":
                parser = new MonpolyTraceParser(null); break;
            case "dejavu":
                parser = new DejavuTraceParser(); break;
            case "dejavu-timed":
                parser = new DejavuTraceParser(true); break;
            default:
                invalidArgument();
                throw new RuntimeException("unreachable");
        }
        if (mode != null)
            parser.setTerminatorMode(mode);
        return parser;
    }

    private static TraceFormatter getTraceFormatter(String format, boolean lazyQuotes) {
        switch (format) {
            case "csv":
                return new Crv2014CsvFormatter();
            case "csv-linear":
                return new Crv2014CsvLinearizingFormatter();
            case "monpoly":
            case "verimon":
                return new MonpolyTraceFormatter(lazyQuotes);
            case "monpoly-linear":
            case "verimon-linear":
                return new MonpolyLinearizingTraceFormatter(lazyQuotes);
            case "dejavu":
                return new DejavuTraceFormatter();
            case "dejavu-linear":
                return new DejavuLinearizingTraceFormatter();
            default:
                invalidArgument();
                throw new RuntimeException("unreachable");
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Replayer replayer = new Replayer();

        String inputFilename = null;
        String outputHost = null;
        String parserType = "csv";
        String formatterType = "csv";
        TraceParser.TerminatorMode mode = null;
        int outputPort = 0;
        int numInputFiles = 1;
        boolean markDatabaseEnd = true;
        boolean clearTopic = false;
        boolean kafkaOutput = false;
        boolean otherBranch = false;
        boolean lazyQuotes = true;

        try {
            for (int i = 0; i < args.length; ++i) {
                switch (args[i]) {
                    case "-h":
                    case "--help":
                        printHelp();
                        return;
                    case "-v":
                        replayer.reporter = new IntervalReporter(false);
                        break;
                    case "-vv":
                        replayer.reporter = new IntervalReporter(true);
                        break;
                    case "--latency-report":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        replayer.reporter = new PercentileReporter(Integer.parseInt(args[i]));
                        break;
                    case "-a":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        replayer.timeMultiplier = Double.parseDouble(args[i]);
                        break;
                    case "-e":
                        replayer.explicitEmissiontime = true;
                        break;
                    case "-q":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        replayer.queueCapacity = Integer.parseInt(args[i]);
                        break;
                    case "-n":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        numInputFiles = Integer.parseInt(args[i]);
                        break;
                    case "-i":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        parserType = args[i];
                        break;
                    case "-f":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        formatterType = args[i];
                        break;
                    case "-m":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        boolean monpolyLinear = Boolean.parseBoolean(args[i]);
                        formatterType = monpolyLinear ? "monpoly-linear" : "monpoly";
                        break;
                    case "-d":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        boolean dejavuLinear = Boolean.parseBoolean(args[i]);
                        formatterType = dejavuLinear ? "dejavu-linear" : "dejavu";
                        break;
                    case "-t":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        replayer.timestampInterval = Long.parseLong(args[i]);
                        break;
                    case "-T":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        replayer.timestampPrefix = args[i];
                        break;
                    case "-o":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        String[] parts = args[i].split(":", 2);
                        if (parts.length != 2) {
                            if (args[i].equals("kafka")) {
                                kafkaOutput = true;
                                break;
                            }
                            invalidArgument();
                        }
                        outputHost = parts[0];
                        outputPort = Integer.parseInt(parts[1]);
                        break;
                    case "-C":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        replayer.commandPrefix = args[i];
                        break;
                    case "--term":
                        if (++i == args.length) {
                            invalidArgument();
                        }
                        switch (args[i]) {
                            case "NO_TERM": mode = TraceParser.TerminatorMode.NO_TERMINATORS; break;
                            case "TIMESTAMPS": mode = TraceParser.TerminatorMode.ONLY_TIMESTAMPS; break;
                            case "TIMEPOINTS": mode = TraceParser.TerminatorMode.ALL_TERMINATORS; break;
                            default: invalidArgument();
                        }
                        break;
                    case "--other_branch":
                        otherBranch = true;
                    case "--clear":
                        clearTopic = true;
                        break;
                    case "-nt":
                    case "-no-end-marker":
                        markDatabaseEnd = false;
                        break;
                    case "--force-quotes":
                        lazyQuotes = false;
                        break;
                    default:
                        if (args[i].startsWith("-") || inputFilename != null) {
                            invalidArgument();
                        }
                        inputFilename = args[i];
                        break;
                }
            }
        } catch (NumberFormatException e) {
            invalidArgument();
        }
        if (numInputFiles == 1 && !kafkaOutput && !otherBranch) {
            BufferedReader input;
            Output output;
            TraceParser parser;
            TraceFormatter formatter;

            if (inputFilename == null) {
                input = new BufferedReader(new InputStreamReader(System.in));
            } else {
                try {
                    input = new BufferedReader(new FileReader(inputFilename));
                } catch (FileNotFoundException e) {
                    System.err.println("Error: " + e.getMessage());
                    System.exit(1);
                    return;
                }
            }

            if (outputHost == null) {
                output = replayer.new StandardOutput();
            } else {
                try {
                    ServerSocket serverSocket = new ServerSocket(outputPort, -1, InetAddress.getByName(outputHost));
                    Socket sock = serverSocket.accept();
                    output = replayer.new SocketOutput(sock);
                } catch (IOException e) {
                    System.err.print("Error: " + e.getMessage() + "\n");
                    System.exit(1);
                    return;
                }
            }
            parser = getTraceParser(parserType, mode);
            formatter = getTraceFormatter(formatterType, lazyQuotes);
            formatter.setMarkDatabaseEnd(markDatabaseEnd);
            ReplayerWorker repWorker = replayer.new ReplayerWorker(input, output, parser, formatter, false);

            repWorker.run();
        } else {
            ArrayList<ReplayerWorker> replayerWorkers = new ArrayList<>();
            ArrayList<Thread> workerThreads = new ArrayList<>();
            KafkaProducer<String, String> producer = null;
            Properties props = new Properties();
            if (clearTopic) {
                props.setProperty("clearTopic", Boolean.toString(clearTopic));
                props.setProperty("numPartitions", Integer.toString(numInputFiles));
            }
            if(kafkaOutput) {
                MonitorKafkaConfig.init(props);
                if (!clearTopic && MonitorKafkaConfig.getNumPartitions() != numInputFiles)
                    throw new IllegalArgumentException("If the topic is not cleared the number of input files must match");
            }
            
            if (inputFilename == null) {
                System.err.println("Multisource only works with file input");
                System.exit(1);
            }

            if(!kafkaOutput && outputHost == null) {
                System.err.println("Socketouput with more than 1 partition requires outputhost");
            }
            if (kafkaOutput)
                producer = new KafkaProducer<>(MonitorKafkaConfig.getKafkaProps());

            ArrayList<SocketOutput> socketClients = new ArrayList<>();
            ServerSocket serverSocket = new ServerSocket(outputPort, -1, InetAddress.getByName(outputHost));
            for (int i = 0; i < numInputFiles; ++i) {
                BufferedReader input;
                try {
                    System.out.println("first replayer worker reading from " + inputFilename + i + ".csv");
                    input = new BufferedReader(new FileReader(inputFilename + i + ".csv"));
                } catch (FileNotFoundException e) {
                    System.err.println("Error: " + e.getMessage());
                    System.exit(1);
                    return;
                }
                Output output;
                if (kafkaOutput) {
                    output = replayer.new KafkaOutput(i, producer);
                } else {
                    Socket sock = serverSocket.accept();
                    SocketOutput socketOutput = replayer.new SocketOutput(sock);
                    socketClients.add(socketOutput);
                    output = socketOutput;
                }
                TraceParser parser = getTraceParser(parserType, mode);
                TraceFormatter formatter = getTraceFormatter(formatterType, lazyQuotes);
                formatter.setMarkDatabaseEnd(markDatabaseEnd);
                replayerWorkers.add(replayer.new ReplayerWorker(input, output, parser, formatter, true));
            }

            for (ReplayerWorker w: replayerWorkers) {
                Thread t = new Thread(w);
                workerThreads.add(t);
                t.start();
            }

            for (Thread t: workerThreads) {
                t.join();
            }

            if (!kafkaOutput) {
                for (SocketOutput o: socketClients)
                    o.closeClient();
            }
        }
    }
}
