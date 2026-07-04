// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation

/// GATT chunk codec matching [`mycelium_core::ble_mesh`] (MYLC chunks).
enum BleMeshChunker {
    private static let chunkMagic: [UInt8] = [0x4D, 0x59, 0x4C, 0x43] // MYLC
    private static let chunkHeader = 6
    private static let maxPayload = 476

    static func chunkFrame(_ frame: Data) -> [Data] {
        guard !frame.isEmpty else { return [] }
        let pieces = stride(from: 0, to: frame.count, by: maxPayload).map { start in
            frame.subdata(in: start ..< min(start + maxPayload, frame.count))
        }
        let total = min(pieces.count, 255)
        return pieces.prefix(total).enumerated().map { idx, piece in
            var out = Data(capacity: chunkHeader + piece.count)
            out.append(contentsOf: chunkMagic)
            out.append(UInt8(idx))
            out.append(UInt8(total))
            out.append(piece)
            return out
        }
    }

    final class Assembler {
        private var total = 0
        private var parts: [Int: Data] = [:]

        func reset() {
            total = 0
            parts.removeAll()
        }

        /// Returns full frame when reassembly completes.
        func push(_ chunk: Data) -> Data? {
            guard chunk.count >= chunkHeader else { return nil }
            guard chunk.prefix(4).elementsEqual(chunkMagic) else { return nil }
            let idx = Int(chunk[4])
            let count = Int(chunk[5])
            guard count > 0, idx < count else { return nil }
            if total == 0 {
                total = count
            } else if total != count {
                reset()
                total = count
            }
            parts[idx] = chunk.subdata(in: chunkHeader ..< chunk.count)
            guard parts.count == total else { return nil }
            var out = Data()
            for i in 0 ..< total {
                guard let piece = parts[i] else { return nil }
                out.append(piece)
            }
            reset()
            return out
        }
    }
}
