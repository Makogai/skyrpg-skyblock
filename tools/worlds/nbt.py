"""A small NBT reader for Minecraft saves: load(path) for gzip'd files, parse(bytes), chunks(region file)."""
import gzip
import io
import struct
import zlib


def _read(buf, tag):
    if tag == 1:
        return struct.unpack(">b", buf.read(1))[0]
    if tag == 2:
        return struct.unpack(">h", buf.read(2))[0]
    if tag == 3:
        return struct.unpack(">i", buf.read(4))[0]
    if tag == 4:
        return struct.unpack(">q", buf.read(8))[0]
    if tag == 5:
        return struct.unpack(">f", buf.read(4))[0]
    if tag == 6:
        return struct.unpack(">d", buf.read(8))[0]
    if tag == 7:
        return buf.read(struct.unpack(">i", buf.read(4))[0])
    if tag == 8:
        return buf.read(struct.unpack(">H", buf.read(2))[0]).decode("utf-8", "replace")
    if tag == 9:
        t = buf.read(1)[0]
        n = struct.unpack(">i", buf.read(4))[0]
        return [_read(buf, t) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            t = buf.read(1)[0]
            if t == 0:
                return out
            name = buf.read(struct.unpack(">H", buf.read(2))[0]).decode("utf-8", "replace")
            out[name] = _read(buf, t)
    if tag == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(">%di" % n, buf.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(">%dq" % n, buf.read(8 * n)))
    raise ValueError(f"tag {tag}")


def parse(data):
    buf = io.BytesIO(data)
    tag = buf.read(1)[0]
    buf.read(struct.unpack(">H", buf.read(2))[0])
    return _read(buf, tag)


def load(path):
    return parse(gzip.open(path).read())


def chunks(region_path):
    """(local x, local z, chunk nbt) for every chunk in an .mca region file."""
    data = open(region_path, "rb").read()
    for i in range(1024):
        offset = int.from_bytes(data[i * 4:i * 4 + 3], "big")
        if offset == 0:
            continue
        start = offset * 4096
        length = struct.unpack(">i", data[start:start + 4])[0]
        raw = data[start + 5:start + 4 + length]
        raw = zlib.decompress(raw) if data[start + 4] == 2 else gzip.decompress(raw) if data[start + 4] == 1 else raw
        yield i % 32, i // 32, parse(raw)


def unpack(longs, bits, count):
    """1.16+ packed indices (entries never span two longs)."""
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    for value in longs:
        value &= (1 << 64) - 1
        for k in range(per):
            out.append((value >> (k * bits)) & mask)
            if len(out) == count:
                return out
    return out


# ---------------------------------------------------------------- typed round trip (for rewriting chunks)
# A tagged value is (tag, value): a compound's value is {name: (tag, value)}, a list's is (element tag, [values]).

def _read_typed(buf, tag):
    if tag == 9:
        t = buf.read(1)[0]
        n = struct.unpack(">i", buf.read(4))[0]
        return (t, [_read_typed(buf, t) for _ in range(n)])
    if tag == 10:
        out = {}
        while True:
            t = buf.read(1)[0]
            if t == 0:
                return out
            name = buf.read(struct.unpack(">H", buf.read(2))[0]).decode("utf-8", "surrogatepass")
            out[name] = (t, _read_typed(buf, t))
    if tag == 8:
        return buf.read(struct.unpack(">H", buf.read(2))[0])  # raw bytes (modified UTF-8), written back as they were
    return _read(buf, tag)


def _write_typed(out, tag, value):
    if tag == 1:
        out.write(struct.pack(">b", value))
    elif tag == 2:
        out.write(struct.pack(">h", value))
    elif tag == 3:
        out.write(struct.pack(">i", value))
    elif tag == 4:
        out.write(struct.pack(">q", value))
    elif tag == 5:
        out.write(struct.pack(">f", value))
    elif tag == 6:
        out.write(struct.pack(">d", value))
    elif tag == 7:
        out.write(struct.pack(">i", len(value)) + bytes(value))
    elif tag == 8:
        out.write(struct.pack(">H", len(value)) + value)
    elif tag == 9:
        element, items = value
        out.write(bytes([element if items else (element or 0)]) + struct.pack(">i", len(items)))
        for item in items:
            _write_typed(out, element, item)
    elif tag == 10:
        for name, (t, v) in value.items():
            raw = name.encode("utf-8", "surrogatepass")
            out.write(bytes([t]) + struct.pack(">H", len(raw)) + raw)
            _write_typed(out, t, v)
        out.write(b"\x00")
    elif tag == 11:
        out.write(struct.pack(">i", len(value)) + struct.pack(">%di" % len(value), *value))
    elif tag == 12:
        out.write(struct.pack(">i", len(value)) + struct.pack(">%dq" % len(value), *value))
    else:
        raise ValueError(f"tag {tag}")


def parse_typed(data):
    """(root name, root compound) with every tag's type kept."""
    buf = io.BytesIO(data)
    buf.read(1)
    name = buf.read(struct.unpack(">H", buf.read(2))[0])
    return name, _read_typed(buf, 10)


def dump_typed(name, compound):
    out = io.BytesIO()
    out.write(b"\x0a" + struct.pack(">H", len(name)) + name)
    _write_typed(out, 10, compound)
    return out.getvalue()


def raw_chunks(region_path):
    """(index, timestamp, decompressed chunk bytes) for every chunk in a region file."""
    data = open(region_path, "rb").read()
    for i in range(1024):
        offset = int.from_bytes(data[i * 4:i * 4 + 3], "big")
        if offset == 0:
            continue
        start = offset * 4096
        length = struct.unpack(">i", data[start:start + 4])[0]
        raw = data[start + 5:start + 4 + length]
        raw = zlib.decompress(raw) if data[start + 4] == 2 else gzip.decompress(raw) if data[start + 4] == 1 else raw
        stamp = struct.unpack(">i", data[4096 + i * 4:4096 + i * 4 + 4])[0]
        yield i, stamp, raw


def write_region(path, chunks):
    """A region file from {index: (timestamp, chunk bytes)}, zlib-compressed, sector-aligned."""
    header = bytearray(8192)
    body = bytearray()
    sector = 2
    for i, (stamp, raw) in sorted(chunks.items()):
        packed = zlib.compress(raw)
        blob = struct.pack(">i", len(packed) + 1) + b"\x02" + packed
        blob += b"\x00" * (-len(blob) % 4096)
        count = len(blob) // 4096
        header[i * 4:i * 4 + 4] = sector.to_bytes(3, "big") + bytes([count])
        header[4096 + i * 4:4096 + i * 4 + 4] = struct.pack(">i", stamp)
        body += blob
        sector += count
    with open(path, "wb") as f:
        f.write(header + body)
