#!/usr/bin/env python3
"""
Look up the frequency rank of a word in a given language using `wordfreq`.

Usage:
    python word_freq.py <word> <lang_code>

Stdout:
    Integer rank (1 = most common word in the language). Empty line if the
    word is not in the top 100,000 of that language, or if wordfreq is not
    installed. The script never raises — it prints "" on any failure so the
    Java caller can treat that as "unknown" without parsing exceptions.

Why top_n_list and not zipf_frequency:
    The user wants a *rank*, not a logarithmic score. `top_n_list(lang, N)`
    returns words ordered most-common-first, so `index(word) + 1` is the rank.
    100k is large enough to catch nearly anything a learner saves and keeps
    the data file under ~5 MB for the common languages.
"""
import sys


def main() -> None:
    if len(sys.argv) < 3:
        print("")
        return

    word = sys.argv[1].strip().lower()
    lang = sys.argv[2].strip().lower()
    if not word or not lang:
        print("")
        return

    try:
        from wordfreq import top_n_list
    except ImportError:
        # wordfreq not installed — degrade silently
        print("")
        return

    try:
        top = top_n_list(lang, 100_000)
        idx = top.index(word)
        print(idx + 1)
    except ValueError:
        # Word not in top 100k — too rare to rank meaningfully
        print("")
    except Exception as e:
        sys.stderr.write(f"word_freq error: {e}\n")
        print("")


if __name__ == "__main__":
    main()
