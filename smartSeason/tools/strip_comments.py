"""Removes comments from generated sources.

Java stripping is literal-aware: a // or /* inside a string or char literal is
content, not a comment, so a naive regex would corrupt the source.
"""


def strip_java(source):
    out = []
    i = 0
    n = len(source)
    while i < n:
        ch = source[i]
        nxt = source[i + 1] if i + 1 < n else ""

        if ch == '"' and source[i:i + 3] == '"""':
            end = source.find('"""', i + 3)
            end = n if end == -1 else end + 3
            out.append(source[i:end])
            i = end
            continue

        if ch == '"':
            j = i + 1
            while j < n:
                if source[j] == "\\":
                    j += 2
                    continue
                if source[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(source[i:j])
            i = j
            continue

        if ch == "'":
            j = i + 1
            while j < n:
                if source[j] == "\\":
                    j += 2
                    continue
                if source[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(source[i:j])
            i = j
            continue

        if ch == "/" and nxt == "/":
            j = source.find("\n", i)
            i = n if j == -1 else j
            continue

        if ch == "/" and nxt == "*":
            j = source.find("*/", i + 2)
            end = n if j == -1 else j + 2

            newline = source.find("\n", end)
            after = source[end:n if newline == -1 else newline]

            k = len(out) - 1
            preceding = []
            while k >= 0 and out[k] != "\n":
                preceding.append(out[k])
                k -= 1
            blank_before = "".join(reversed(preceding)).strip() == ""

            if blank_before and after.strip() == "":
                del out[k + 1:]
                i = end
                while i < n and source[i] in " \t":
                    i += 1
                if i < n and source[i] == "\n":
                    i += 1
            else:
                i = end
            continue

        out.append(ch)
        i += 1

    text = "".join(out)
    lines = [line.rstrip() for line in text.split("\n")]

    cleaned = []
    for line in lines:
        if line == "" and cleaned and cleaned[-1] == "":
            continue
        cleaned.append(line)

    while cleaned and cleaned[0] == "":
        cleaned.pop(0)
    while cleaned and cleaned[-1] == "":
        cleaned.pop()
    return "\n".join(cleaned) + "\n"


def strip_hash(source):
    lines = [l for l in source.split("\n") if not l.lstrip().startswith("#")]
    return _squeeze(lines)


def strip_sql(source):
    lines = [l for l in source.split("\n") if not l.lstrip().startswith("--")]
    return _squeeze(lines)


def _squeeze(lines):
    cleaned = []
    for line in (l.rstrip() for l in lines):
        if line == "" and cleaned and cleaned[-1] == "":
            continue
        cleaned.append(line)
    while cleaned and cleaned[0] == "":
        cleaned.pop(0)
    while cleaned and cleaned[-1] == "":
        cleaned.pop()
    return "\n".join(cleaned) + "\n"
