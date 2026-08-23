# 44. PDF, Print & Share (Phase 7D)

## PDF - Android only, in this phase

`data/rendering/PdfDocumentRenderer.kt` uses Android's built-in `android.graphics.pdf.PdfDocument`
(no third-party PDF library added - avoids a new dependency and any licensing question for a
single-page, text/table layout). It lives in the `data` layer, not `domain`, because it needs an
Android `Context` for private-file output - the accounting/rendering *domain* must never import a
PDF/Android-framework type (Section 14), and it doesn't: `JsonDocumentRenderer`
(`domain/rendering/`) has zero Android dependency, `PdfDocumentRenderer` (`data/rendering/`) is
where that boundary is drawn.

`PdfDocumentRenderer.render(context, data, template)` draws a simple, template-driven layout -
header (business name, document type/number/date), buyer, a line-item table respecting
`TemplateLayout.visibleColumns`, totals, and terms - deliberately plain (Section 11: no
drag-and-drop editor). Every value drawn comes from `DocumentData`; this class performs no
accounting/GST calculation of any kind. Output is written to `context.filesDir/documents/`.

**Not available server-side.** `GET /documents/{id}/pdf` always responds
`501 PDF_NOT_AVAILABLE_SERVER_SIDE` - no PDF-generation library was added to the Python server in
this phase (Section 29 frames server-side rendering as "if later required," not mandatory now).
This is an explicit, documented extension point.

## Known test-environment limitation

`android.graphics.pdf.*` is real Android framework code with no functioning behavior under a plain
JVM unit test - exercising it requires Robolectric (or an instrumented device/emulator). This
project's Robolectric setup is already broken by an unrelated environment issue (`DefaultSdkProvider`
throwing `UnsupportedOperationException` - the same root cause behind the 3 known pre-existing
Robolectric failures every phase's test run already carries). Rather than add a 4th Robolectric
test that would fail for the same pre-existing environment reason (and risk being mistaken for a
regression by anyone checking "only 3 known failures"), `PdfDocumentRenderer`'s actual byte
generation is **not** exercised by `Phase7DTestSuite` - written as production-quality code, verified
by inspection, not by a passing JVM test. Everything it depends on - `assembleDocumentData`,
`resolveTemplateForRender`, the output-path construction - IS fully unit-tested; only the literal
`PdfDocument`/`Canvas`/`Paint` drawing calls are outside this environment's test reach.

## Print

`data/rendering/PrintAdapter.kt` wraps Android's `PrintManager`/`PrintDocumentAdapter` around an
already-generated PDF `File` - it reads bytes and writes them to the print spooler; it never
recomputes anything, and it consumes the *same* artifact `PdfDocumentRenderer` produces (Section
16: "do not create a second invoice calculation path for printing"). Same test-environment
limitation as PDF generation - the class exists and is production-quality but isn't independently
JVM-tested.

## Share

`data/rendering/ShareAdapter.kt` builds an `Intent.ACTION_SEND` for an already-generated PDF `File`,
exposed via a `FileProvider` content URI (never a raw `file://` path) - `buildShareIntent` is a pure
function of a `File`; it never mutates the invoice/voucher it came from, never regenerates the
document (Section 17). Requires the `com.example.accounting.fileprovider` `<provider>` declared in
`AndroidManifest.xml` (new in this phase, `res/xml/file_paths.xml` scoped to the app's private
`documents/` directory only) - this manifest/resource addition is infrastructure, not a UI change;
no screen references `ShareAdapter` yet.

## Nothing here is wired to a screen

`PdfDocumentRenderer`/`PrintAdapter`/`ShareAdapter` are all callable, production-quality
infrastructure with zero call sites in `presentation/` - per the Phase 7 gate, a future Phase 7J
screen will be the first thing to actually invoke them.
