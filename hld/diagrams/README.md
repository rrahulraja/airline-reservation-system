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

## Exporting to PNG or PDF

No draw.io CLI is installed in this environment, so export is a manual step:

1. Open the file in diagrams.net or draw.io Desktop.
2. **File > Export as > PNG**, with *Transparent Background* off and *Zoom* 200%
   for a crisp image in the HLD.
3. Save beside the source as `NN-name.png`.

`hld/architecture.md` references the PNG exports. Keep the `.drawio` files as the
editable source of truth and re-export after any change.

For `hld/architecture.pdf` (the brief asks for a PDF), export the whole Markdown
document once at the end rather than after each edit.
