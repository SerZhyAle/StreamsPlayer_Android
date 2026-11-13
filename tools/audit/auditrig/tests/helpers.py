"""Shared helpers of the fixture suite."""
import csv
import json
import os
import random
import tempfile

from auditrig import sampler

DATA = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'data')
PACKAGE = 'com.sza.streamsplayer'


def data(name):
    with open(os.path.join(DATA, name), encoding='utf-8') as handle:
        return handle.read()


class FakeClock:
    """A clock that only moves when told to. sleep() advances it, so a sampling loop runs in no real time."""

    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


def write_sampler_csv(path, seconds, columns, ok=None):
    """Write a sampler CSV from {column: values}; absent columns stay empty."""
    with open(path, 'w', newline='', encoding='utf-8') as handle:
        writer = csv.writer(handle)
        writer.writerow(sampler.COLUMNS)
        for index, t in enumerate(seconds):
            row = []
            for name in sampler.COLUMNS:
                if name == 'elapsed_s':
                    row.append(t)
                elif name == 'ok':
                    row.append(1 if ok is None else ok[index])
                else:
                    values = columns.get(name)
                    row.append('' if values is None or values[index] is None else values[index])
            writer.writerow(row)


def series(seconds, base, per_hour=0.0, noise=0.0, seed=1):
    rng = random.Random(seed)
    return [base + per_hour * t / 3600.0 + (rng.gauss(0, noise) if noise else 0.0) for t in seconds]


def tempdir():
    return tempfile.TemporaryDirectory(prefix='auditrig-')


def write_text(path, text):
    with open(path, 'w', encoding='utf-8') as handle:
        handle.write(text)


def write_json(path, document):
    write_text(path, json.dumps(document))


def read_json(path):
    with open(path, encoding='utf-8') as handle:
        return json.load(handle)
