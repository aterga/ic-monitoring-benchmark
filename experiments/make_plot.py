#!/usr/bin/env python

"""
Parses replayer statistics from the online monitoring experiments and creates plots with the
results.
"""

import glob
import logging
import os.path
import re

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

# Plot styling
plt.rcParams.update({
    'figure.dpi': 300,
    'figure.figsize': (4.8, 1.6),
    'axes.linewidth': 0.5,
    'xtick.major.width': 0.5,
    'ytick.major.width': 0.5,
    'lines.linewidth': 0.5,
    'lines.markeredgewidth': 0.5,
    'lines.markersize': 3,
    'font.family': 'Nimbus Sans',
    'font.size': 6,
    'axes.titlesize': 6,
})

index_rate_re = re.compile(r'.*: \((\d+),(\d+)\)\s*$')

def load_index_rate(filename):
    """Loads the index rate graph from the output produced by monitoring index_rate.mfotl."""
    data = []
    with open(filename, 'r') as f:
        for l in f:
            m = index_rate_re.match(l)
            if m:
                ts = int(m.group(1))
                n = int(m.group(2))
                data.append((ts, n))
    min_ts = data[0][0]
    max_ts = data[-1][0]
    ir = np.zeros(max_ts - min_ts + 1, dtype=float)
    for ts, n in data:
        ir[ts - min_ts] = n
    return ir

def smooth(x, decay):
    """Simple peak-preserving smoothing function."""
    y = np.zeros(x.shape[0], dtype=float)
    y[0] = x[0]
    for i in range(1, x.shape[0]):
        y[i] = max(x[i], y[i-1]*decay)
    return y

def try_load(filename, name, skiplast=False):
    """Load replayer statistics."""
    if os.path.exists(filename):
        df = pd.read_csv(filename, header=0, index_col=0, skipinitialspace=True,
                skipfooter=1 if skiplast else 0, engine='python')
    else:
        logging.warning("%s: File not found, cannot create plot for %s", filename, name)
        return None

    df.name = name
    return df

def make_plot(index_rate, policies):
    ir_coarse = smooth(index_rate, 0.998)
    ir_fine = smooth(index_rate, 0.98)

    col1 = '50%'
    col2 = '90%'
    colx = 'max'
    ylim = 1000

    fig, axs = plt.subplots(1, len(policies), sharey=True)
    axs2 = [ax.twinx() for ax in axs]
    axs[-1].get_shared_y_axes().join(*axs2)
    for axsnd in axs2[:-1]:
        axsnd.yaxis.set_tick_params(labelright=False)

    axs[0].set_ylabel(f'Latency [ms]')
    axs[0].set_ylim(0, ylim)
    axs2[-1].set_ylabel('Index rate [1000/s]', color='b')
    axs2[0].set_ylim(0, 1.5)

    for ax, axsnd, data in zip(axs, axs2, policies):
        ax.set_title(data.name)
        if data.name in ['logging-behavior']:
            ir = ir_fine
        else:
            ir = ir_coarse
        ax.set_xlim(0, data.index.max())
        ax.set_xlabel('Time since log start [s]')
        ax.plot(data.index, data[colx], 'kx', label='maximum')
        ax.plot(data.index, data[col2], '-', label='90th percentile', color='#00b000')
        ax.plot(data.index, data[col1], '-', label='50th percentile', color='#ffe550')
        axsnd.plot(data.index, ir[data.index]/1000, 'b-', label='index rate', linewidth=0.25)
        
    lines, labels = axs[0].get_legend_handles_labels()
    axs2[0].legend(lines, labels, loc='upper left', framealpha=1)
    fig.tight_layout(pad=0.2)

if __name__ == '__main__':
    index_rate = load_index_rate('data/mainnet_index_rate.txt')
    policies = list(filter(lambda x: x is not None, [
            try_load('data/online/clean_logs/report.txt', 'clean-logs'),
            try_load('data/online/reboot_count/report.txt', 'reboot-count'),
            # Skip the last line because it contains the timeout message.
            try_load('data/online/logging_behavior__exe/report.txt', 'logging-behavior', skiplast=True),
            #try_load('data/online/unauth/report.txt', 'unauthorized-connections', skiplast=True),
            ]))
    assert len(policies) > 0, "no policies were specified"
    make_plot(index_rate, policies)
    plt.savefig('data/online/latency.png')

