// SPDX-License-Identifier: LGPL-2.1-or-later
// Wire-compatible subset of the Bachata-S4 Vortek handshake protocol:
// zenithblue-oss/shadps4-arm64 runtime/vortek-protocol/bachata_vortek_protocol.h
// The pinned glibc client sends this before Vortek's create-context request.
#ifndef RB4DX_BACHATA_VORTEK_PROTOCOL_H
#define RB4DX_BACHATA_VORTEK_PROTOCOL_H

#include <stdint.h>

#define BACHATA_VORTEK_MAGIC UINT32_C(0x4254564B)
#define BACHATA_VORTEK_PROTO_MAJOR 1
#define BACHATA_VORTEK_PROTO_MINOR 0
#define BACHATA_VORTEK_ENDIAN_LITTLE 1
#define REQUEST_CODE_BACHATA_HANDSHAKE 3
#define BACHATA_VORTEK_HANDSHAKE_OK 0
#define BACHATA_VORTEK_HANDSHAKE_MISMATCH 2

typedef struct BachataVortekHandshakeRequest {
    uint32_t magic;
    uint16_t proto_major;
    uint16_t proto_minor;
    uint16_t pointer_size;
    uint16_t endianness;
    uint32_t vulkan_header_version;
    char client_build_id[64];
} BachataVortekHandshakeRequest;

typedef struct BachataVortekHandshakeResponse {
    uint32_t magic;
    uint16_t proto_major;
    uint16_t proto_minor;
    uint16_t status;
    uint16_t reserved;
} BachataVortekHandshakeResponse;

_Static_assert(sizeof(BachataVortekHandshakeRequest) == 80, "Vortek handshake request layout changed");
_Static_assert(sizeof(BachataVortekHandshakeResponse) == 12, "Vortek handshake response layout changed");

#endif
