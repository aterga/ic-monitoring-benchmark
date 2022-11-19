#!/usr/bin/env python

"""
Parses pipeline statistics from the offline monitoring experiments and prints a table with the
results.
"""

import glob
import logging
import os.path
import re
import sys
import yaml

import numpy as np
import pandas as pd

sys.path.append('policy-monitoring')
import util.yaml  # required for parsing custom data types in the yaml files

BASEDIR = './data'


log_stats_cache = {}

def get_log_stats(name):
    """Compute size statistics for the raw and processed logs of the given pot."""
    global log_stats_cache
    if name in log_stats_cache:
        return log_stats_cache[name]

    # Raw log
    found = glob.glob(BASEDIR + '/**/' + name + '.log', recursive=True)
    if len(found) >= 1:
        filename = found[0]
        if len(found) > 1:
            logging.warning("Multiple raw logs matching %s, choosing %s", name, filename)
        raw_size = os.path.getsize(filename) / 1024 / 1024  # MiB
        with open(filename, 'r') as f:
            raw_entries = sum(1 for line in f if not line.startswith(']'))
    else:
        logging.error("No raw log matching %s found", name)
        raw_size = np.nan
        raw_entries = np.nan

    # Processed log
    found = glob.glob(BASEDIR + '/**/' + name + '-*.unipol.log', recursive=True)
    if len(found) >= 1:
        # Multiple matches expected, files should be identical
        filename = found[0]
        processed_size = os.path.getsize(filename) / 1024 / 1024  # MiB
    else:
        logging.error("No processed log matching %s found", name)
        processed_size = np.nan

    stats = {'raw_size': raw_size, 'raw_entries': raw_entries, 'processed_size': processed_size}
    log_stats_cache[name] = stats
    return stats

def load_stat_file(scenario, filename):
    """Extract the relevant measurements from a yaml file produced by the pipeline.

    The returned DataFrame has the following columns:
      - scenario: test or prod
      - pot: name of the system test, or 'mainnet*' for the production log
      - policy: name of the monitored policy
      - exit_code: of MonPoly
      - test_runtime: duration of the system test execution (seconds)
      - num_events: number of events in the preprocessed log
      - num_violations: number of violations (lines) reported by MonPoly
      - monpoly_mem: maximum resident set size of the MonPoly process (MiB)
      - process_time: elapsed real time spent in the pipeline's wrapper around MonPoly (seconds)
      - preprocessor_time: elapsed real time spent preprocessing
      - nodes: initial number of IC nodes
      - subnets: initial number of IC subnets
      - raw_entries: number of entries in the raw log
      - raw_size: size of the raw log (MiB)
      - processed_size: size of the processed log (MiB)
    """
    logging.info("Loading %s", filename)
    with open(filename, 'r') as f:
        raw = yaml.full_load(stream=f)
    data = []
    for pot, potdata in raw.items():
        log_stats = get_log_stats(pot)
        preproc = potdata['pre_processor']
        global_infra = potdata['global_infra']
        nodes = len(global_infra['original_nodes'])
        subnets = len(global_infra['original_subnet_types'])
        mdata = potdata['monpoly']
        if len(mdata) > 1:
            logging.warning("%s: Preprocessing shared by %d policies in pot %s)",
                    filename, len(mdata), pot)
        for policy, poldata in mdata.items():
            if 'perf_metrics' in poldata:
                perf = poldata['perf_metrics']
                row = [
                        scenario,
                        pot.removesuffix('--pseudo'),
                        policy,
                        int(poldata['exit_code']),
                        float(preproc['test_runtime_milliseconds']) / 1000,
                        int(poldata['num_events']),
                        int(poldata['num_violations_reported']),
                        float(perf['maximum_resident_set_size_kbytes']) / 1024,
                        float(poldata['process_duration_seconds']),
                        float(preproc['pre_processing']['perf_counter_seconds']),
                        nodes,
                        subnets,
                        log_stats['raw_entries'],
                        log_stats['raw_size'],
                        log_stats['processed_size']
                        ]
                data.append(row)
            else:
                logging.warning("%s: No metrics for policy %s in pot %s. Timeout?", filename, policy, pot)
    return pd.DataFrame(data, columns=['scenario', 'pot', 'policy', 'exit_code', 'test_runtime',
                                       'num_events', 'num_violations', 'monpoly_mem',
                                       'process_time', 'preprocessor_time', 'nodes', 'subnets',
                                       'raw_entries', 'raw_size', 'processed_size'])

def load():
    """Load all measurements from the offline monitoring experiments."""
    logging.info("Searching for pipeline stats in %s", BASEDIR)
    ds = []
    for filename in glob.glob(BASEDIR + '/offline/*/*/stat.yaml'):
        m = re.match(r'.*/(production|system-tests)/[^/]+/stat\.yaml$', filename)
        if m:
            ds.append(load_stat_file(m.group(1), filename))
    logging.info("Loaded all files")

    # Combine pipeline stats
    data = pd.concat(ds)
    data.reset_index(drop=True, inplace=True)

    # Compute derived statistics
    data['monpoly_ntime'] = data['process_time'] / data['num_events'] * 1000  # milliseconds
    data['preproc_ntime'] = data['preprocessor_time'] / data['raw_entries'] * 1000  # milliseconds
    data['event_rate'] = data['num_events'] / data['test_runtime']
    return data

def print_table(data):
    """Pretty-print result table to stdout."""

    TABLE_HEADER = '{:25} | {:25} | {:25}'
    TABLE_ROW1 = '{:25} | {:>11} ({:>11}) | {:>11} ({:>11})'
    TABLE_ROW2 = '{:25} | {:>4} ({:>6}) {:>4} ({:>4}) | {:>4} ({:>6}) {:>4} ({:>4})'
    TABLE_SEP = 81 * '-'

    # Log statistics and preprocessor time
    logs = data[data['policy'] == 'clean_logs'].groupby('scenario').agg(
            {
                'raw_entries': [np.median, np.max],
                'raw_size': [np.median, np.max],
                'num_events': [np.median, np.max],
                'event_rate': [np.median, np.max],
                'processed_size': [np.median, np.max],
            })
    preproc = data.groupby('scenario').agg({'preproc_ntime': [np.median, np.max]})

    common = pd.concat([logs, preproc], axis=1).stack(level=0).unstack(level='scenario')
    common.columns = common.columns.reorder_levels([1, 0])
    common.sort_index(axis=1, ascending=False, inplace=True)

    print(TABLE_SEP)
    print(TABLE_HEADER.format('Measurement', 'Testing', 'Prod'))
    print(TABLE_SEP)
    print(TABLE_ROW1.format('Raw log entries',
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('system-tests', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('system-tests', 'amax'), np.nan)),
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('production', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['raw_entries'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Raw log size',
                            '{:.1f} '.format(common.loc['raw_size'].get(('system-tests', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['raw_size'].get(('system-tests', 'amax'), np.nan)),
                            '{:.1f} '.format(common.loc['raw_size'].get(('production', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['raw_size'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Processed events',
                            '{:.0f}   '.format(common.loc['num_events'].get(('system-tests', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['num_events'].get(('system-tests', 'amax'), np.nan)),
                            '{:.0f}   '.format(common.loc['num_events'].get(('production', 'median'), np.nan)),
                            '{:.0f}   '.format(common.loc['num_events'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Processed events/s',
                            '{:.1f} '.format(common.loc['event_rate'].get(('system-tests', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['event_rate'].get(('system-tests', 'amax'), np.nan)),
                            '{:.1f} '.format(common.loc['event_rate'].get(('production', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['event_rate'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Processed log size',
                            '{:.1f} '.format(common.loc['processed_size'].get(('system-tests', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['processed_size'].get(('system-tests', 'amax'), np.nan)),
                            '{:.1f} '.format(common.loc['processed_size'].get(('production', 'median'), np.nan)),
                            '{:.1f} '.format(common.loc['processed_size'].get(('production', 'amax'), np.nan))))
    print(TABLE_ROW1.format('Preprocessor time',
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('system-tests', 'median'), np.nan)),
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('system-tests', 'amax'), np.nan)),
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('production', 'median'), np.nan)),
                            '{:.2f}'.format(common.loc['preproc_ntime'].get(('production', 'amax'), np.nan))))
    print(TABLE_SEP)

    # Performance per policy
    perf = data.groupby(['scenario', 'policy']).agg({'monpoly_ntime': [np.median, np.max],
                                                    'monpoly_mem': [np.median, np.max]}).unstack(0)
    perf.columns = perf.columns.reorder_levels([2, 0, 1])
    perf.sort_index(axis=1, ascending=False, inplace=True)

    POLICIES = [
            'clean_logs',
            'logging_behavior__exe',
            'unauthorized_connections',
            'reboot_count',
            'finalization_consistency',
            'finalized_height',
            'replica_divergence',
            'block_validation_latency'
            ]

    for p in POLICIES:
        if p not in perf.index:
            continue
        r = perf.loc[p]
        print(TABLE_ROW2.format(p,
                               '{:.2f}'.format(r.get(('system-tests', 'monpoly_ntime', 'median'), np.nan)),
                               '{:.1f}'.format(r.get(('system-tests', 'monpoly_ntime', 'amax'), np.nan)),
                               '{:.0f}'.format(r.get(('system-tests', 'monpoly_mem', 'median'), np.nan)),
                               '{:.0f}'.format(r.get(('system-tests', 'monpoly_mem', 'amax'), np.nan)),
                               '{:.2f}'.format(r.get(('production', 'monpoly_ntime', 'median'), np.nan)),
                               '{:.1f}'.format(r.get(('production', 'monpoly_ntime', 'amax'), np.nan)),
                               '{:.0f}'.format(r.get(('production', 'monpoly_mem', 'median'), np.nan)),
                               '{:.0f}'.format(r.get(('production', 'monpoly_mem', 'amax'), np.nan))))
    print(TABLE_SEP)

if __name__ == '__main__':
    data = load()
    print_table(data)
