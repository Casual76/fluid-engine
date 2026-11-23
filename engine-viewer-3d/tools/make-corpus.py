"""Build original GLB, glTF, STL and OBJ samples with Python 3. Pillow makes the texture."""
from io import BytesIO
import json
from math import cos, pi, sin
from pathlib import Path
from struct import pack

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/androidTest/assets/corpus"
ROOT.mkdir(parents=True, exist_ok=True)


def torus(major_segments, minor_segments, radius, tube, height=0):
    positions, normals, uv, indices = [], [], [], []
    for j in range(major_segments + 1):
        u = j * 2 * pi / major_segments
        for k in range(minor_segments + 1):
            v = k * 2 * pi / minor_segments
            positions.append(((radius + tube * cos(v)) * cos(u),
                              height + tube * sin(v),
                              (radius + tube * cos(v)) * sin(u)))
            normals.append((cos(v) * cos(u), sin(v), cos(v) * sin(u)))
            uv.append((j / major_segments, k / minor_segments))
    row = minor_segments + 1
    for j in range(major_segments):
        for k in range(minor_segments):
            p = j * row + k
            indices.extend((p, p + 1, p + row, p + 1, p + row + 1, p + row))
    return positions, normals, uv, indices


class Gltf:
    def __init__(self):
        self.binary = bytearray()
        self.views = []
        self.accessors = []
        self.meshes = []
        self.nodes = []
        self.materials = []
        self.images = []
        self.textures = []
        self.animations = []

    def view(self, data, target=None):
        result = {"buffer": 0, "byteOffset": len(self.binary), "byteLength": len(data)}
        if target:
            result["target"] = target
        self.views.append(result)
        self.binary.extend(data)
        self.binary.extend(bytes((-len(data)) % 4))
        return len(self.views) - 1

    def accessor(self, values, kind, component=5126, target=None, bounds=False):
        wide = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[kind]
        code = {5126: "f", 5125: "I", 5123: "H"}[component]
        if wide == 1:
            data = pack(f"<{len(values)}{code}", *values)
        else:
            data = b"".join(pack(f"<{wide}{code}", *value) for value in values)
        result = {"bufferView": self.view(data, target), "componentType": component,
                  "count": len(values), "type": kind}
        if bounds:
            result["min"] = [min(row[i] for row in values) for i in range(wide)] if wide != 1 else [min(values)]
            result["max"] = [max(row[i] for row in values) for i in range(wide)] if wide != 1 else [max(values)]
        self.accessors.append(result)
        return len(self.accessors) - 1

    def mesh(self, position, normal, uv, indices, palette, metallic, roughness, texture=False):
        pbr = {"baseColorFactor": palette, "metallicFactor": metallic, "roughnessFactor": roughness}
        if texture:
            pbr["baseColorTexture"] = {"index": 0}
        self.materials.append({"pbrMetallicRoughness": pbr})
        p = self.accessor(position, "VEC3", target=34962, bounds=True)
        n = self.accessor(normal, "VEC3", target=34962)
        t = self.accessor(uv, "VEC2", target=34962)
        i = self.accessor(indices, "SCALAR", 5123 if max(indices) < 65536 else 5125, 34963)
        self.meshes.append({"primitives": [{"indices": i, "material": len(self.materials) - 1,
                            "attributes": {"POSITION": p, "NORMAL": n, "TEXCOORD_0": t}}]})
        self.nodes.append({"mesh": len(self.meshes) - 1})

    def motion(self, node, axis):
        times = self.accessor([0., 0.75, 1.5, 2.25, 3.0], "SCALAR", bounds=True)
        values = []
        for index in range(5):
            a = pi * index / 4
            values.append(tuple(axis[i] * sin(a) for i in range(3)) + (cos(a),))
        rotations = self.accessor(values, "VEC4")
        self.animations.append({"name": "Rotazione", "samplers": [{"input": times, "output": rotations}],
                                "channels": [{"sampler": 0, "target": {"node": node, "path": "rotation"}}]})

    def write(self, path, sidecar=None):
        external = {"uri": sidecar} if sidecar else {}
        doc = dict(asset={"version": "2.0"}, scene=0, scenes=[{"nodes": getattr(self, "root_only", list(range(len(self.nodes))))}],
                   nodes=self.nodes, meshes=self.meshes, materials=self.materials,
                   buffers=[{"byteLength": len(self.binary)} | external],
                   bufferViews=self.views, accessors=self.accessors)
        if self.images:
            doc.update(images=self.images, textures=self.textures)
        if self.animations:
            doc["animations"] = self.animations
        if sidecar:
            path.write_text(json.dumps(doc, indent=2), encoding="utf-8")
            (path.parent / sidecar).write_bytes(self.binary)
        else:
            blob = json.dumps(doc, separators=(",", ":")).encode()
            blob += b" " * ((-len(blob)) % 4)
            data = pack("<4sIIII", b"glTF", 2, 28 + len(blob) + len(self.binary), len(blob), 0x4e4f534a)
            path.write_bytes(data + blob + pack("<II", len(self.binary), 0x004e4942) + self.binary)


image = Image.new("RGB", (64, 64), "#284a62")
draw = ImageDraw.Draw(image)
for x in range(-32, 64, 16):
    draw.line((x, 0, x + 32, 64), fill="#efbd80", width=5)
image.save(ROOT / "tessuto.png")

rotor = Gltf()
rotor.mesh(*torus(56, 16, 1.0, 0.19), [0.035, 0.31, 0.41, 1], .75, .29)
rotor.mesh(*torus(44, 14, 0.7, 0.13), [0.9, 0.37, 0.11, 1], .72, .31)
# Tilt the inner ring to make the animation visible from any starting angle.
rotor.nodes[1]["rotation"] = [sin(pi / 5), 0, 0, cos(pi / 5)]
rotor.motion(1, (1, 0, 0))
rotor.write(ROOT / "anelli.glb")

external = Gltf()
external.images = [{"uri": "tessuto.png"}]
external.textures = [{"source": 0}]
external.mesh(*torus(32, 12, 1, .28), [1., 1., 1., 1.], .0, .7, texture=True)
external.write(ROOT / "tessuto.gltf", "tessuto.bin")

obj = """
mtllib materiali.mtl
v -1 0 -1
v 1 0 -1
v 1 0 1
v -1 0 1
v 0 1.4 0
vt 0 0
vt 1 0
vt .5 1
usemtl rame
f 1/1 2/2 5/3
f 3/1 4/2 5/3
usemtl tessuto
f 2/1 3/2 5/3
f 4/1 1/2 5/3
f 4/1 3/2 2/3 1/1
"""
(ROOT / "piramide.obj").write_text(obj, encoding="utf-8")
(ROOT / "materiali.mtl").write_text("newmtl rame\nKd .8 .34 .14\nNs 80\nnewmtl tessuto\nKd 1 1 1\nmap_Kd tessuto.png\n", encoding="utf-8")
positions = [(-1, 0, -1), (1, 0, -1), (1, 0, 1), (-1, 0, 1), (0, 1.4, 0)]
faces = [(0, 1, 4), (1, 2, 4), (2, 3, 4), (3, 0, 4), (3, 2, 1), (3, 1, 0)]
stl_ascii = ["solid piramide"]
stl_binary = bytearray(b"Piramide Fluid".ljust(80, b" ") + pack("<I", len(faces)))
for face in faces:
    stl_ascii.extend([" facet normal 0 0 0", "  outer loop"])
    points = [positions[i] for i in face]
    for point in points:
        stl_ascii.append("   vertex {} {} {}".format(*point))
    stl_ascii.extend(["  endloop", " endfacet"])
    stl_binary.extend(pack("<12fH", 0., 0., 0., *(axis for point in points for axis in point), 0))
stl_ascii.append("endsolid piramide")
(ROOT / "piramide-ascii.stl").write_text("\n".join(stl_ascii), encoding="utf-8")
(ROOT / "piramide-binaria.stl").write_bytes(stl_binary)

# 50,000 triangles per ring. glTF instances reuse the same buffers but
# each ring contributes its own draw and its own triangles to the scene.
for count, name in [(5, "250mila.glb"), (20, "milione.glb")]:
    large = Gltf()
    large.mesh(*torus(200, 125, 1., .055), [.06, .4, .53, 1.], .55, .29)
    transforms = []
    for i in range(count):
        phase = i * pi / count
        transforms.append({"mesh": 0,
                           "scale": [0.55 + 0.028 * i] * 3,
                           "translation": [0, (i - (count - 1) / 2) * .07, 0],
                           "rotation": [0.0, 0.0, sin(phase), cos(phase)]})
    # Only parent nodes are roots. The child meshes rotate as one scene.
    large.nodes = transforms + [{"children": list(range(count))}]
    large.root_only = [count]
    large.motion(count, (0, 1, 0))
    large.write(ROOT / name)
