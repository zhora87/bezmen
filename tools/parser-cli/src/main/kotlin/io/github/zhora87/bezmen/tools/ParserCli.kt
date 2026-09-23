package io.github.zhora87.bezmen.tools

import io.github.zhora87.bezmen.domain.LocalePack
import io.github.zhora87.bezmen.domain.Money
import io.github.zhora87.bezmen.domain.OcrLine
import io.github.zhora87.bezmen.domain.Quantity
import io.github.zhora87.bezmen.domain.parser.Field
import io.github.zhora87.bezmen.domain.parser.ParseResult
import io.github.zhora87.bezmen.domain.parser.ParsedTag
import io.github.zhora87.bezmen.domain.parser.PriceTagParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.system.exitProcess

/** What a corpus case should parse to. Lives in corpus/expected/<id>.json. */
@Serializable
data class ExpectedTag(
    val pack: String,
    val price: Money? = null,
    val oldPrice: Money? = null,
    val quantity: Quantity? = null,
    val isWeighted: Boolean = false,
    val note: String = "",
    /** Skeleton written by tools/corpus/ingest.py that nobody has filled in yet. */
    val pending: Boolean = false,
)

data class Options(
    val corpusDir: File,
    val engine: String,
    val packsDir: File?,
    val minAccuracy: Double,
    val verbose: Boolean,
    /** Print the OCR lines and the full parse result of this case instead of the report. */
    val explain: String?,
)

data class CaseResult(
    val id: String,
    val pack: String,
    val priceOk: Boolean,
    val quantityOk: Boolean,
    val weightedOk: Boolean,
    val kind: String,
    /** "got ... / expected ..." for the report. */
    val detail: String,
) {
    val allOk: Boolean get() = priceOk && quantityOk && weightedOk
}

class UsageError(message: String) : IllegalArgumentException(message)

private const val EXIT_ACCURACY = 1
private const val EXIT_USAGE = 2
private const val PERCENT = 100.0
private const val QUANTITY_TOLERANCE = 0.5

private const val USAGE = """
bezmen parser-cli: runs the price tag parser over the corpus and prints per-field accuracy.

usage: parser-cli <corpus-dir> [--engine <id>] [--packs-dir <dir>] [--min-accuracy <0..1>] [--verbose]

  <corpus-dir>      directory with ocr/<engine>/<id>.json and expected/<id>.json
  --engine          which OCR dump set to read (default: synthetic)
  --packs-dir       load locale packs from this directory instead of the bundled ones
  --min-accuracy    exit with code 1 if the share of fully correct cases is below this
  --verbose         print every case, not only the failures
  --explain <id>    print the OCR lines and the parse result of one case
"""

fun main(args: Array<String>) {
    val options = try {
        parseArgs(args)
    } catch (e: UsageError) {
        println(e.message)
        println(USAGE.trimIndent())
        exitProcess(EXIT_USAGE)
    }
    if (options.explain != null) {
        explain(options, options.explain)
        return
    }
    val results = runCorpus(options)
    if (results.isEmpty()) {
        println("No cases found under ${options.corpusDir}/expected for engine '${options.engine}'.")
        exitProcess(EXIT_USAGE)
    }
    printReport(results, options.verbose)
    val accuracy = results.count { it.allOk }.toDouble() / results.size
    if (accuracy < options.minAccuracy) {
        val message = "FAIL: accuracy %.1f%% is below the required %.1f%%"
        println(message.format(Locale.ROOT, accuracy * PERCENT, options.minAccuracy * PERCENT))
        exitProcess(EXIT_ACCURACY)
    }
}

private fun parseArgs(args: Array<String>): Options {
    val corpus = args.firstOrNull()?.takeUnless { it.startsWith("--") }
        ?: throw UsageError("corpus directory is required")
    val reader = OptionReader(args.drop(1))
    val options = Options(
        corpusDir = File(corpus),
        engine = reader.string("--engine") ?: "synthetic",
        packsDir = reader.string("--packs-dir")?.let(::File),
        minAccuracy = reader.double("--min-accuracy") ?: 0.0,
        verbose = reader.flag("--verbose"),
        explain = reader.string("--explain"),
    )
    reader.rejectUnknown()
    return options
}

/** Minimal `--name value` / `--flag` reader; every unknown token is an error. */
private class OptionReader(private val args: List<String>) {
    private val seen = mutableSetOf<Int>()

    fun flag(name: String): Boolean {
        val i = args.indexOf(name)
        if (i >= 0) seen += i
        return i >= 0
    }

    fun string(name: String): String? {
        val i = args.indexOf(name)
        if (i < 0) return null
        seen += i
        seen += i + 1
        return args.getOrNull(i + 1) ?: throw UsageError("missing value for $name")
    }

    fun double(name: String): Double? = string(name)?.let {
        it.toDoubleOrNull() ?: throw UsageError("$name needs a number")
    }

    fun rejectUnknown() {
        val unknown = args.indices.firstOrNull { it !in seen } ?: return
        throw UsageError("unknown option ${args[unknown]}")
    }
}

private val json = Json { ignoreUnknownKeys = false }

private fun runCorpus(options: Options): List<CaseResult> {
    val expectedDir = File(options.corpusDir, "expected")
    val ocrDir = File(options.corpusDir, "ocr/${options.engine}")
    val parsers = mutableMapOf<String, PriceTagParser>()
    val files = expectedDir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
    return files.mapNotNull { file ->
        val id = file.nameWithoutExtension
        val ocrFile = File(ocrDir, "$id.json")
        if (!ocrFile.exists()) {
            System.err.println("skip $id: no OCR dump at $ocrFile")
            return@mapNotNull null
        }
        val expected = json.decodeFromString(ExpectedTag.serializer(), file.readText())
        if (expected.pending) {
            System.err.println("skip $id: expected values still pending")
            return@mapNotNull null
        }
        val parser = parsers.getOrPut(expected.pack) { PriceTagParser(loadPack(expected.pack, options.packsDir)) }
        val lines = json.decodeFromString(ListSerializer(OcrLine.serializer()), ocrFile.readText())
        compare(id, expected, parser.parse(lines))
    }
}

private fun explain(options: Options, id: String) {
    val expectedFile = File(options.corpusDir, "expected/$id.json")
    val ocrFile = File(options.corpusDir, "ocr/${options.engine}/$id.json")
    val expected = json.decodeFromString(ExpectedTag.serializer(), expectedFile.readText())
    val lines = json.decodeFromString(ListSerializer(OcrLine.serializer()), ocrFile.readText())
    val pack = loadPack(expected.pack, options.packsDir)
    val summary = describe(expected.price, expected.oldPrice, expected.quantity)
    println("== $id [${expected.pack}] expected $summary weighted=${expected.isWeighted}")
    lines.forEachIndexed { i, l ->
        val b = l.box
        val geometry = "y %.2f-%.2f  x %.2f-%.2f  h %.2f"
            .format(Locale.ROOT, b.top, b.bottom, b.left, b.right, b.height)
        println("  %2d  %s  c %.2f  %s".format(Locale.ROOT, i, geometry, l.confidence, l.text))
    }
    when (val result = PriceTagParser(pack).parse(lines)) {
        is ParseResult.Success -> {
            val overall = "%.2f".format(Locale.ROOT, result.overall)
            println("-> Success overall=$overall\n${result.tag.pretty()}")
        }
        is ParseResult.NeedsInput -> println("-> NeedsInput missing=${result.missing}\n${result.tag.pretty()}")
        ParseResult.Nothing -> println("-> Nothing")
    }
}

private fun ParsedTag.pretty(): String = buildString {
    fun <T> row(label: String, field: Field<T>?, show: (T) -> String) {
        append("   $label: ")
        if (field == null) {
            append("-\n")
            return
        }
        val confidence = "%.2f".format(Locale.ROOT, field.confidence)
        append("${show(field.value)} conf=$confidence lines=${field.sourceLines}")
        if (field.alternatives.isNotEmpty()) append(" alt=${field.alternatives.map(show)}")
        append('\n')
    }
    row("price", price) { it.format() }
    row("old", oldPrice) { it.format() }
    row("quantity", quantity) { "${it.value} ${it.unit.code}" }
    row("printedUnitPrice", printedUnitPrice) { "%.4f minor per base".format(Locale.ROOT, it.minorPerBaseUnit) }
    row("name", name) { it }
    append("   weighted: $isWeighted")
}

private fun loadPack(id: String, packsDir: File?): LocalePack {
    val text = if (packsDir != null) {
        File(packsDir, "$id.json").readText()
    } else {
        Thread.currentThread().contextClassLoader.getResource("$id.json")?.readText()
            ?: error("locale pack '$id' is not bundled; pass --packs-dir")
    }
    return LocalePack.fromJson(text).also { pack ->
        val problems = pack.validate()
        check(problems.isEmpty()) { "locale pack '$id' is invalid: $problems" }
    }
}

private fun compare(id: String, expected: ExpectedTag, result: ParseResult): CaseResult {
    val tag = when (result) {
        is ParseResult.Success -> result.tag
        is ParseResult.NeedsInput -> result.tag
        ParseResult.Nothing -> null
    }
    val priceOk = tag?.price?.value == expected.price
    val quantityOk = sameQuantity(tag?.quantity?.value, expected.quantity)
    val weightedOk = (tag?.isWeighted ?: false) == expected.isWeighted
    val detail = "got ${describe(tag?.price?.value, tag?.oldPrice?.value, tag?.quantity?.value)} / " +
        "expected ${describe(expected.price, expected.oldPrice, expected.quantity)}"
    return CaseResult(id, expected.pack, priceOk, quantityOk, weightedOk, result::class.simpleName ?: "?", detail)
}

private fun describe(price: Money?, old: Money?, quantity: Quantity?): String {
    val p = price?.format() ?: "-"
    val o = old?.let { " (old ${it.format()})" } ?: ""
    val q = quantity?.let { "${it.value} ${it.unit.code}" } ?: "-"
    return "$p$o @ $q"
}

private fun sameQuantity(actual: Quantity?, expected: Quantity?): Boolean {
    if (actual == null || expected == null) return actual == expected
    if (actual.dimension != expected.dimension) return false
    return abs(actual.toBase().value - expected.toBase().value) < QUANTITY_TOLERANCE
}

private fun printReport(results: List<CaseResult>, verbose: Boolean) {
    results.filter { verbose || !it.allOk }.forEach { r ->
        val marks = listOf("price" to r.priceOk, "quantity" to r.quantityOk, "weighted" to r.weightedOk)
            .joinToString(" ") { (name, ok) -> if (ok) "$name:ok" else "$name:FAIL" }
        val detail = if (r.allOk) "" else "  ${r.detail}"
        println("${if (r.allOk) "ok  " else "FAIL"} ${r.id} [${r.pack}] ${r.kind} $marks$detail")
    }
    println()
    println("pack        cases  price   quantity  all")
    (results.groupBy { it.pack } + ("TOTAL" to results)).forEach { (pack, rs) ->
        val price = rs.count { it.priceOk } * PERCENT / rs.size
        val quantity = rs.count { it.quantityOk } * PERCENT / rs.size
        val all = rs.count { it.allOk } * PERCENT / rs.size
        println("%-11s %5d  %5.1f%%  %6.1f%%  %5.1f%%".format(Locale.ROOT, pack, rs.size, price, quantity, all))
    }
}
