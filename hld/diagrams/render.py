#!/usr/bin/env python3
"""
Renders the .drawio sources in this directory to SVG and PNG.

The .drawio files remain the editable source of truth; the images are generated so
they can be embedded in hld/architecture.md and never drift from the source by hand.

Usage:
    pip install cairosvg
    python3 hld/diagrams/render.py

Covers the subset of mxGraph used by these diagrams: rectangles (optionally
rounded or dashed), ellipses, cylinders, UML lifelines, text blocks, and edges with
block or open arrowheads. It is deliberately not a general mxGraph renderer.
"""

import html
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PADDING = 24
FONT = "DejaVu Sans, Helvetica, Arial, sans-serif"


def parse_style(style):
    """mxGraph styles are 'key=value;flag;key=value' strings."""
    result = {}
    for part in (style or "").split(";"):
        part = part.strip()
        if not part:
            continue
        if "=" in part:
            key, value = part.split("=", 1)
            result[key.strip()] = value.strip()
        else:
            result[part] = "1"
    return result


def label_lines(raw):
    """Turn an mxGraph HTML label into plain text lines."""
    if not raw:
        return []
    text = html.unescape(raw)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.I)
    text = re.sub(r"<hr[^>]*>", "\n", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    lines = [line.strip() for line in text.split("\n")]
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return lines


def geometry_of(cell):
    geo = cell.find("mxGeometry")
    if geo is None:
        return None
    def num(attr, default=0.0):
        value = geo.get(attr)
        return float(value) if value is not None else default
    return {
        "x": num("x"), "y": num("y"),
        "width": num("width"), "height": num("height"),
        "source": geo.find("mxPoint[@as='sourcePoint']"),
        "target": geo.find("mxPoint[@as='targetPoint']"),
        "points": geo.find("Array[@as='points']"),
    }


def point(element):
    if element is None:
        return None
    return float(element.get("x", 0)), float(element.get("y", 0))


def esc(text):
    return html.escape(text, quote=True)


def wrap(lines, width, size):
    """Greedy wrap to the shape width, approximating glyph width for the font."""
    if width <= 0:
        return lines
    per_char = size * 0.55
    budget = max(4, int((width - 16) / per_char))
    wrapped = []
    for line in lines:
        if len(line) <= budget:
            wrapped.append(line)
            continue
        current = ""
        for word in line.split(" "):
            candidate = f"{current} {word}".strip()
            if len(candidate) <= budget:
                current = candidate
            else:
                if current:
                    wrapped.append(current)
                current = word
        if current:
            wrapped.append(current)
    return wrapped


def render_text(out, lines, x, y, width, height, style, default_anchor="middle"):
    if not lines:
        return
    size = float(style.get("fontSize", 12))
    lines = wrap(lines, width, size)
    bold = style.get("fontStyle") in ("1", "3")
    italic = style.get("fontStyle") in ("2", "3")
    colour = style.get("fontColor", "#111111")
    align = style.get("align", default_anchor)
    anchor = {"left": "start", "center": "middle", "right": "end"}.get(align, "middle")

    line_height = size * 1.35
    block = line_height * len(lines)
    vertical = style.get("verticalAlign", "middle")
    if vertical == "top":
        first = y + size + 4
    elif vertical == "bottom":
        first = y + height - block + size
    else:
        first = y + (height - block) / 2 + size

    if anchor == "start":
        text_x = x + float(style.get("spacingLeft", 8) or 8)
    elif anchor == "end":
        text_x = x + width - 8
    else:
        text_x = x + width / 2

    weight = ' font-weight="bold"' if bold else ""
    slant = ' font-style="italic"' if italic else ""
    for index, line in enumerate(lines):
        if not line:
            continue
        out.append(
            f'<text x="{text_x:.1f}" y="{first + index * line_height:.1f}" '
            f'font-family="{FONT}" font-size="{size:.1f}" fill="{colour}"'
            f' text-anchor="{anchor}"{weight}{slant}>{esc(line)}</text>')


def render_vertex(out, cell, style, geo):
    x, y, w, h = geo["x"], geo["y"], geo["width"], geo["height"]
    fill = style.get("fillColor", "#ffffff")
    if fill == "none":
        fill = "none"
    stroke = style.get("strokeColor", "#333333")
    if stroke == "none":
        stroke = "none"
    stroke_width = style.get("strokeWidth", "1")
    dash = ' stroke-dasharray="8 6"' if style.get("dashed") == "1" else ""
    opacity = float(style.get("opacity", 100)) / 100

    shape = style.get("shape", "")
    is_text = "text" in style and "shape" not in style

    if is_text:
        render_text(out, label_lines(cell.get("value")), x, y, w, h, style)
        return

    if "ellipse" in style:
        out.append(
            f'<ellipse cx="{x + w / 2:.1f}" cy="{y + h / 2:.1f}" rx="{w / 2:.1f}" ry="{h / 2:.1f}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{stroke_width}" opacity="{opacity}"/>')
    elif shape.startswith("cylinder"):
        lip = min(14.0, h / 4)
        out.append(
            f'<path d="M {x:.1f} {y + lip:.1f} '
            f'A {w / 2:.1f} {lip:.1f} 0 0 1 {x + w:.1f} {y + lip:.1f} '
            f'L {x + w:.1f} {y + h - lip:.1f} '
            f'A {w / 2:.1f} {lip:.1f} 0 0 1 {x:.1f} {y + h - lip:.1f} Z" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{stroke_width}"/>')
        out.append(
            f'<path d="M {x:.1f} {y + lip:.1f} A {w / 2:.1f} {lip:.1f} 0 0 0 {x + w:.1f} {y + lip:.1f}" '
            f'fill="none" stroke="{stroke}" stroke-width="{stroke_width}"/>')
    elif shape == "umlLifeline":
        header = float(style.get("size", 40))
        out.append(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{header:.1f}" rx="4" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{stroke_width}"/>')
        out.append(
            f'<line x1="{x + w / 2:.1f}" y1="{y + header:.1f}" x2="{x + w / 2:.1f}" '
            f'y2="{y + h:.1f}" stroke="{stroke}" stroke-width="1" stroke-dasharray="6 5"/>')
        render_text(out, label_lines(cell.get("value")), x, y, w, header, style)
        return
    else:
        radius = 8 if style.get("rounded") == "1" else 0
        out.append(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{radius}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{stroke_width}" '
            f'opacity="{opacity}"{dash}/>')

    render_text(out, label_lines(cell.get("value")), x, y, w, h, style)


def centre(cell_geo):
    return (cell_geo["x"] + cell_geo["width"] / 2, cell_geo["y"] + cell_geo["height"] / 2)


def attach(source_geo, target_geo):
    """
    Boundary points and an elbow between two shapes.

    mxGraph's orthogonalEdgeStyle routes around obstacles; this approximates it well
    enough to keep connectors off the boxes they pass, which is what matters for
    legibility.
    """
    sx, sy = centre(source_geo)
    tx, ty = centre(target_geo)

    vertical_gap = abs(ty - sy) > abs(tx - sx)

    if vertical_gap:
        if ty > sy:
            start = (sx, source_geo["y"] + source_geo["height"])
            end = (tx, target_geo["y"])
        else:
            start = (sx, source_geo["y"])
            end = (tx, target_geo["y"] + target_geo["height"])
        middle = (start[1] + end[1]) / 2
        elbows = [] if abs(sx - tx) < 1 else [(start[0], middle), (end[0], middle)]
    else:
        if tx > sx:
            start = (source_geo["x"] + source_geo["width"], sy)
            end = (target_geo["x"], ty)
        else:
            start = (source_geo["x"], sy)
            end = (target_geo["x"] + target_geo["width"], ty)
        middle = (start[0] + end[0]) / 2
        elbows = [] if abs(sy - ty) < 1 else [(middle, start[1]), (middle, end[1])]

    return start, elbows, end


def render_edge(out, cell, style, geo, vertices):
    source = point(geo["source"]) if geo else None
    target = point(geo["target"]) if geo else None
    routed = []

    source_id, target_id = cell.get("source"), cell.get("target")
    if source is None and target is None and source_id in vertices and target_id in vertices:
        source, routed, target = attach(vertices[source_id], vertices[target_id])
    else:
        if source is None and source_id in vertices:
            source = centre(vertices[source_id])
        if target is None and target_id in vertices:
            target = centre(vertices[target_id])
    if source is None or target is None:
        return

    stroke = style.get("strokeColor", "#333333")
    width = style.get("strokeWidth", "1")
    dash = ' stroke-dasharray="7 5"' if style.get("dashed") == "1" else ""

    waypoints = list(routed)
    if geo and geo["points"] is not None:
        waypoints = [point(p) for p in geo["points"].findall("mxPoint")]

    path = [source] + waypoints + [target]
    points_attr = " ".join(f"{px:.1f},{py:.1f}" for px, py in path)

    marker = ""
    if style.get("endArrow", "block") not in ("none", "0"):
        marker = ' marker-end="url(#arrow-open)"' if style.get("endArrow") == "open" \
            else ' marker-end="url(#arrow-block)"'

    out.append(
        f'<polyline points="{points_attr}" fill="none" stroke="{stroke}" '
        f'stroke-width="{width}"{dash}{marker}/>')

    lines = label_lines(cell.get("value"))
    if lines:
        mid_index = len(path) // 2
        if len(path) % 2 == 0:
            ax, ay = path[mid_index - 1]
            bx, by = path[mid_index]
            mx, my = (ax + bx) / 2, (ay + by) / 2
        else:
            mx, my = path[mid_index]
        size = float(style.get("fontSize", 10))
        colour = style.get("fontColor", "#333333")
        for index, line in enumerate(lines):
            ly = my - 6 - (len(lines) - 1 - index) * size * 1.2
            # White halo first, so labels stay readable where they cross a connector.
            for paint in (f'fill="none" stroke="#ffffff" stroke-width="4" '
                          f'stroke-linejoin="round"', f'fill="{colour}"'):
                out.append(
                    f'<text x="{mx:.1f}" y="{ly:.1f}" font-family="{FONT}" '
                    f'font-size="{size:.1f}" {paint} text-anchor="middle">{esc(line)}</text>')


def to_svg(drawio_path):
    root = ET.parse(drawio_path).getroot()
    model = root.find(".//mxGraphModel")
    cells = model.find("root").findall("mxCell")

    vertices = {}
    for cell in cells:
        if cell.get("vertex") == "1":
            geo = geometry_of(cell)
            if geo:
                vertices[cell.get("id")] = geo

    max_x = max((g["x"] + g["width"] for g in vertices.values()), default=800)
    max_y = max((g["y"] + g["height"] for g in vertices.values()), default=600)
    for cell in cells:
        if cell.get("edge") == "1":
            geo = geometry_of(cell)
            for p in (point(geo["source"]), point(geo["target"])) if geo else ():
                if p:
                    max_x, max_y = max(max_x, p[0]), max(max_y, p[1])

    width = int(max_x + PADDING)
    height = int(max_y + PADDING)

    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        '<defs>',
        '<marker id="arrow-block" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
        'markerHeight="7" orient="auto-start-reverse">'
        '<path d="M 0 0 L 10 5 L 0 10 z" fill="context-stroke"/></marker>',
        '<marker id="arrow-open" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" '
        'markerHeight="8" orient="auto-start-reverse">'
        '<path d="M 0 0 L 10 5 L 0 10" fill="none" stroke="context-stroke" '
        'stroke-width="1.5"/></marker>',
        '</defs>',
        f'<rect width="{width}" height="{height}" fill="#ffffff"/>',
    ]

    # Vertices first, then edges, so connectors are not hidden behind panels.
    for cell in cells:
        if cell.get("vertex") == "1":
            geo = geometry_of(cell)
            if geo:
                render_vertex(out, cell, parse_style(cell.get("style")), geo)

    for cell in cells:
        if cell.get("edge") == "1":
            render_edge(out, cell, parse_style(cell.get("style")), geometry_of(cell), vertices)

    out.append("</svg>")
    return "\n".join(out), width, height


def main():
    directory = Path(__file__).parent
    sources = sorted(directory.glob("*.drawio"))
    if not sources:
        print("No .drawio files found", file=sys.stderr)
        return 1

    try:
        import cairosvg
    except ImportError:
        print("cairosvg is required: pip install cairosvg", file=sys.stderr)
        return 1

    for source in sources:
        svg, width, height = to_svg(source)
        svg_path = source.with_suffix(".svg")
        png_path = source.with_suffix(".png")
        svg_path.write_text(svg)
        # 2x scale so the PNG stays legible when embedded in the HLD.
        cairosvg.svg2png(bytestring=svg.encode(), write_to=str(png_path),
                         output_width=width * 2, output_height=height * 2)
        print(f"{source.name} -> {svg_path.name}, {png_path.name} ({width}x{height})")

    return 0


if __name__ == "__main__":
    sys.exit(main())
