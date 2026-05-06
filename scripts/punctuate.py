#!/usr/bin/env python3
"""
Restores punctuation in subtitle entries using the
oliverguhr/fullstop-punctuation-multilang-large model via Hugging Face transformers.

The entire transcript is processed as one continuous stream for best context,
then redistributed back to the original subtitle slots by word count.
The model only inserts punctuation after words — it never adds or removes words —
so the total word count is stable and redistribution is exact.

Supported languages: Czech, German, English, Spanish, French, Italian, Polish, Slovak.

Input  (stdin):  JSON array [{"startMs": int, "durationMs": int, "text": str}, ...]
Output (stdout): Same JSON array with punctuated text
On error:        JSON object {"error": str}
"""

import sys
import io
import json

# Force UTF-8 on stdout/stdin — Windows defaults to CP850 / CP1252
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stdin  = io.TextIOWrapper(sys.stdin.buffer,  encoding="utf-8", errors="replace")

# Sentencepiece word-boundary marker (U+2581 LOWER ONE EIGHTH BLOCK)
SP_WORD_START = "▁"

MODEL_NAME = "oliverguhr/fullstop-punctuation-multilang-large"

# Labels produced by oliverguhr/fullstop-punctuation-multilang-large.
# The model uses the punctuation character itself as the entity label.
# "0" means no punctuation after this token.
PUNCT_MAP = {
    ",": ",",
    ".": ".",
    "?": "?",
    "!": "!",
}

# Maximum words per inference chunk (BERT/XLM-R max ≈ 512 tokens; 200 words is safe)
CHUNK_WORDS = 200

# Special tokens to skip during reconstruction
SPECIAL_TOKENS = {"[CLS]", "[SEP]", "[PAD]", "<s>", "</s>", "<pad>"}


def restore_punctuation(pipe, text: str) -> str:
    """
    Run the NER pipeline on `text` in windows of CHUNK_WORDS words.
    Reconstructs words from sentencepiece sub-tokens (XLM-RoBERTa style):
      - Tokens starting with U+2581 (▁) begin a new word.
      - Other tokens are sub-word continuations of the current word.
    Appends the punctuation character indicated by the last sub-token's label.
    Returns the punctuated text with the same number of whitespace-separated words
    as the input (word count is preserved).
    """
    words = text.split()
    if not words:
        return text

    result_words = []

    for chunk_start in range(0, len(words), CHUNK_WORDS):
        chunk = words[chunk_start : chunk_start + CHUNK_WORDS]
        chunk_text = " ".join(chunk)
        predictions = pipe(chunk_text)

        current_pieces = []   # character pieces of the word being assembled
        current_label  = "0"  # punctuation label of the last sub-token

        def flush():
            word_text = "".join(current_pieces).strip()
            if word_text:
                punct = PUNCT_MAP.get(current_label, "")
                result_words.append(word_text + punct)

        for token in predictions:
            piece = token["word"]
            label = token["entity"]

            if piece in SPECIAL_TOKENS:
                continue

            if piece.startswith(SP_WORD_START):
                # New word — flush the previous one, then start fresh
                flush()
                current_pieces = [piece[1:]]  # strip the ▁ marker
                current_label  = label
            else:
                # Sub-token continuation of the current word
                current_pieces.append(piece)
                current_label = label  # last sub-token's label determines punctuation

        flush()  # flush the final word in this chunk

    return " ".join(result_words)


def main():
    try:
        from transformers import pipeline
    except ImportError:
        print(json.dumps({
            "error": "transformers not installed. Run: pip install transformers"
        }))
        sys.exit(1)

    raw = sys.stdin.read().strip().lstrip(SP_WORD_START).lstrip('﻿')
    if not raw:
        print(json.dumps({"error": "Empty input"}))
        sys.exit(1)

    entries = json.loads(raw)
    if not entries:
        print(json.dumps([]))
        return

    # Record original word count per entry for later redistribution
    word_counts = [len(e["text"].split()) for e in entries]

    # Concatenate all entry texts into one continuous stream so the model
    # sees full cross-sentence context.
    full_text = " ".join(e["text"] for e in entries)

    # aggregation_strategy=None is the modern replacement for grouped_entities=False.
    # It returns one dict per sub-token rather than grouped entity spans.
    pipe = pipeline(
        "ner",
        model=MODEL_NAME,
        aggregation_strategy=None,
    )

    punctuated_full = restore_punctuation(pipe, full_text)

    # Redistribute punctuated words back to original slots by word count.
    # Because the model never adds or removes words, total count is stable.
    punctuated_words = punctuated_full.split()
    input_total      = sum(word_counts)

    # Safety check: if counts diverge (edge-case tokenisation), fall back gracefully
    if len(punctuated_words) != input_total:
        sys.stderr.write(
            f"[punctuate] word count mismatch: input={input_total}, "
            f"output={len(punctuated_words)} — returning original text\n"
        )
        print(json.dumps(entries, ensure_ascii=False))
        return

    result = []
    idx = 0
    for i, entry in enumerate(entries):
        wc    = word_counts[i]
        chunk = punctuated_words[idx : idx + wc]
        idx  += wc
        text  = " ".join(chunk).strip() if chunk else entry["text"]
        result.append({
            "startMs":    entry["startMs"],
            "durationMs": entry["durationMs"],
            "text":       text,
        })

    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
