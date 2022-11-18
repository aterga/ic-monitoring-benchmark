package ch.ethz.infsec.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CustomSignature implements Signature {
    private static final long serialVersionUID = -8291442170298800030L;

    private final List<String> events;
    private final Map<String, List<DataType>> parameters;

    protected CustomSignature(List<String> events, Map<String, List<DataType>> parameters){
        this.events = events;
        this.parameters = parameters;
    }

    public CustomSignature(Map<String, List<DataType>> events){
        this.events = new ArrayList<>(events.keySet());
        this.parameters = events;
    }

    public List<String> getEvents(){
        return events;
    }

    public int getArity(String e){
        return parameters.get(e).size();
    }

    public List<DataType> getTypes(String e) {
        return parameters.get(e);
    }

    private static final Pattern specificationPattern =
            Pattern.compile("(([a-zA-Z0-9_-]+)\\(([a-zA-Z0-9_:-]+(?:,\\s*[a-zA-Z0-9:_-]+)*)?\\)\\s*)");
    private static final Pattern argumentDelimiter = Pattern.compile(",\\s*");

    private static DataType parseType(String s) {
        String[] parts = s.trim().split(":");
        String typeName;
        if (parts.length == 1) {
            typeName = parts[0];
        } else {
            typeName = parts[1];
        }
        switch (typeName) {
            case "int": return DataType.INTEGRAL;
            case "float": return DataType.FLOAT;
            case "string": return DataType.STRING;
            default: throw new SignatureParseException("Unknown type " + s);
        }
    }

    public static CustomSignature parse(String path) throws SignatureParseException, IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));
        List<String> events = new ArrayList<>();
        Map<String, List<DataType>> parameters = new HashMap<>();

        for (String pattern : lines) {
            final Matcher matcher = specificationPattern.matcher(pattern);
            while (matcher.regionStart() < pattern.length()) {
                if (!matcher.find() || matcher.group(2) == null) {
                    throw new SignatureParseException("Syntax error in signature");
                }
                String event = matcher.group(2);
                events.add(event);
                if(matcher.group(3)!=null){
                    final String[] args = argumentDelimiter.split(matcher.group(3));
                    parameters.put(event,
                            Arrays.stream(args).map(CustomSignature::parseType).collect(Collectors.toList()));
                } else {
                    parameters.put(event, Collections.emptyList());
                }
                matcher.region(matcher.end(), pattern.length());
            }

        }
        if(events.size()==0){
            System.err.println("[Warning] Empty signature provided. Using the default one: P1(int)");
            events.add("P1");
            parameters.put("P1", Collections.singletonList(DataType.INTEGRAL));
        }

        return new CustomSignature(events, parameters);
    }

    @Override
    public String getString(){
        StringBuilder signature = new StringBuilder();
        for (String event : events) {
            signature.append(event).append('(');
            final List<DataType> types = parameters.get(event);
            for (int i = 0; i < types.size(); ++i) {
                if (i > 0) {
                    signature.append(',');
                }
                signature.append(types.get(i));
            }
            signature.append(")\n");
        }
        return signature.toString();
    }

}
