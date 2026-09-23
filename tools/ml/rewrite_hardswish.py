#!/usr/bin/env python3
"""Replace every HardSwish node with HardSigmoid(alpha=1/6, beta=0.5) followed by Mul.

The ONNX Runtime native library shipped in the Maven `com.microsoft.onnxruntime:onnxruntime`
artifact computes HardSwish incorrectly (verified with 1.20.0 and 1.30.0 on linux-x64: every
model containing the op returns an input-independent result), while the PyPI build is fine.
HardSwish(x) = x * HardSigmoid(x, 1/6, 0.5) exactly, so the rewrite changes nothing numerically;
it is verified below on random input and on every model in models.lock.

    tools/ml/.venv/bin/python tools/ml/rewrite_hardswish.py <in.onnx> <out.onnx>
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import helper


def rewrite(model: onnx.ModelProto) -> int:
    nodes = []
    count = 0
    for node in model.graph.node:
        if node.op_type != "HardSwish":
            nodes.append(node)
            continue
        x, y = node.input[0], node.output[0]
        gate = f"{y}_hardsigmoid"
        nodes.append(helper.make_node("HardSigmoid", [x], [gate], alpha=1.0 / 6, beta=0.5, name=f"{node.name}_hardsigmoid"))
        nodes.append(helper.make_node("Mul", [x, gate], [y], name=f"{node.name}_mul"))
        count += 1
    del model.graph.node[:]
    model.graph.node.extend(nodes)
    return count


def verify(src: Path, dst: Path) -> float:
    a = ort.InferenceSession(str(src), providers=["CPUExecutionProvider"])
    b = ort.InferenceSession(str(dst), providers=["CPUExecutionProvider"])
    inp = a.get_inputs()[0]
    shape = [d if isinstance(d, int) and d > 0 else fallback for d, fallback in zip(inp.shape, [1, 3, 64, 128])]
    x = np.random.default_rng(0).standard_normal(shape).astype(np.float32)
    ya = a.run(None, {inp.name: x})[0]
    yb = b.run(None, {inp.name: x})[0]
    return float(np.abs(ya - yb).max())


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    model = onnx.load(str(src))
    count = rewrite(model)
    onnx.checker.check_model(model)
    onnx.save(model, str(dst))
    diff = verify(src, dst)
    print(f"{src.name} -> {dst.name}: {count} HardSwish nodes rewritten, max abs diff vs original {diff:.2e}")
    return 0 if diff < 1e-5 else 1


if __name__ == "__main__":
    sys.exit(main())
