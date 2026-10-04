# Diagrams

Source files are draw.io / diagrams.net XML (`.drawio`). Open them with any of:

- **diagrams.net** — https://app.diagrams.net, File > Open From > Device
- **VS Code** — the *Draw.io Integration* extension (`hediet.vscode-drawio`) renders
  and edits `.drawio` files inline
- **draw.io Desktop** — https://github.com/jgraph/drawio-desktop/releases

| File | What it shows |
|---|---|
| `01-component-architecture.drawio` | The eight packages, the strict controller to service to repository layering, and `FlightInstanceResolver` as the single seam between `booking` and `flight`. |
| `02-er-diagram.drawio` | All eight tables with keys, cardinalities, and `ux_seat_active` called out as the double-booking guarantee. |
| `03-booking-sequence.drawio` | `POST /api/bookings` end to end, with the nine ordered validations, the transaction boundary, and both the success and conflict paths through one code path. |
| `04-concurrent-booking.drawio` | Two customers claiming one seat: both reach the INSERT, the index decides, one gets 201 and one gets 409. Includes the five concurrency layers. |
| `05-booking-state-machine.drawio` | The `HELD` / `CONFIRMED` / `CANCELLED` / `EXPIRED` lifecycle, each transition's effect on seat inventory, and every rejected transition with its error code. |

## Generated images

Each diagram ships as three files:

| Extension | Role |
|---|---|
| `.drawio` | The editable source of truth. Edit this. |
| `.svg` | Generated. Vector, scales cleanly, good for the HLD on screen. |
| `.png` | Generated at 2x. Embedded in `hld/architecture.md` and in the PDF export. |

**Regenerate after editing any `.drawio`:**

```bash
pip install cairosvg
python3 hld/diagrams/render.py
```

`render.py` parses the mxGraph XML and emits SVG, then rasterizes with cairosvg. It
covers the subset of mxGraph these diagrams use — rectangles, ellipses, cylinders,
UML lifelines, text blocks, and edges with block or open arrowheads — and is
deliberately not a general mxGraph renderer.

Generating the images rather than exporting by hand means they cannot silently drift
from the source, and anyone cloning the repo can rebuild them without installing
draw.io. If you prefer draw.io's own rendering, **File > Export as > PNG** at 200%
zoom produces an equivalent file; just overwrite the generated one.

For `hld/architecture.pdf` (the brief asks for a PDF), export the whole Markdown
document once at the end rather than after each edit.
