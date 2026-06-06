# -*- coding: utf-8 -*-
# version: v0.1.0
# editor: python-coder

"""Print the current Git branch name.

This module exposes a small command-line helper that shells out to
``git rev-parse --abbrev-ref HEAD`` and prints the resulting branch
name to standard output.
"""

import subprocess


def main() -> None:
    """Print the current Git branch name.

    Runs ``git rev-parse --abbrev-ref HEAD`` via :mod:`subprocess` and
    writes the trimmed stdout to standard output. Exits with a
    non-zero status if Git is unavailable or the call fails.
    """
    result = subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    print(result.stdout.strip())


if __name__ == "__main__":
    main()
