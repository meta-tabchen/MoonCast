"""Read ELF dynamic string tags without platform-specific readelf tooling."""
import struct


def dynamic_strings(data):
    if data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5] != 1:
        raise ValueError('Expected 32/64-bit little-endian Android ELF')
    wide = data[4] == 2
    offset = struct.unpack_from('<Q' if wide else '<I', data, 32 if wide else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if wide else 42)
    segments = []
    dynamic = None
    for index in range(count):
        header = struct.unpack_from('<IIQQQQQQ' if wide else '<IIIIIIII', data, offset + size * index)
        kind = header[0]
        file_offset, address, file_size = (header[2], header[3], header[5]) if wide else (header[1], header[2], header[4])
        if kind == 1:
            segments.append((address, file_offset, file_size))
        elif kind == 2:
            dynamic = (file_offset, file_size)
    if dynamic is None:
        raise ValueError('No ELF dynamic section')
    tags = []
    for offset in range(dynamic[0], sum(dynamic), 16 if wide else 8):
        tag, value = struct.unpack_from('<qQ' if wide else '<iI', data, offset)
        if tag == 0:
            break
        tags.append((tag, value))
    table = next(value for tag, value in tags if tag == 5)
    table_size = next(value for tag, value in tags if tag == 10)
    table_offset = next(file_offset + table - address for address, file_offset, size in segments if address <= table < address + size)
    strings = data[table_offset:table_offset + table_size]

    def text(offset):
        return strings[offset:strings.index(b'\0', offset)].decode('utf-8')

    return {'needed': [text(value) for tag, value in tags if tag == 1],
            'soname': next((text(value) for tag, value in tags if tag == 14), None)}
