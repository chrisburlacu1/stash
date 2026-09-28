package dev.cburlacu.stash.data.local

/**
 * Normalizes and standardizes AI-generated and user-provided tags across open domains.
 *
 * Implements English morphological singularization/lemmatization, protects invariant nouns
 * and technical terms, filters out generic format noise words, and applies smart title casing
 * while preserving acronyms and domain-specific capitalization.
 */
object TagNormalizer {

    private val FORMAT_NOISE_WORDS = setOf(
        "podcast", "podcasts", "audio", "episode", "episodes",
        "article", "articles", "video", "videos", "post", "posts",
        "website", "websites", "newsletter", "newsletters", "blog", "blogs",
        "link", "links", "web page", "webpage", "webpages", "paper", "papers",
        "doc", "docs", "documentation", "tutorial", "tutorials", "guide", "guides",
        "overview", "summary", "discussion", "discussions", "read", "reads", "reading",
        "clip", "clips", "stream", "streams", "recording", "recordings",
    )

    private val MINOR_WORDS = setOf(
        "and", "or", "of", "for", "in", "on", "to", "the", "a", "an", "vs", "with", "&",
    )

    private val INVARIANT_TERMS = mapOf(
        // Operating Systems & Platforms
        "ios" to "iOS",
        "macos" to "macOS",
        "ipados" to "iPadOS",
        "tvos" to "tvOS",
        "watchos" to "watchOS",

        // Engineering & Ops
        "devops" to "DevOps",
        "mlops" to "MLOps",
        "gitops" to "GitOps",
        "llmops" to "LLMOps",
        "secops" to "SecOps",
        "sysops" to "SysOps",

        // JS / Web Frameworks
        "node.js" to "Node.js",
        "nodejs" to "Node.js",
        "vue.js" to "Vue.js",
        "vuejs" to "Vue.js",
        "next.js" to "Next.js",
        "nextjs" to "Next.js",
        "nuxt.js" to "Nuxt.js",
        "nuxtjs" to "Nuxt.js",
        "three.js" to "Three.js",
        "threejs" to "Three.js",
        "d3.js" to "D3.js",
        "d3js" to "D3.js",
        "chart.js" to "Chart.js",
        "chartjs" to "Chart.js",
        "express.js" to "Express.js",
        "expressjs" to "Express.js",
        "reactjs" to "React",
        "react.js" to "React",

        // Databases & Tools
        "postgres" to "Postgres",
        "postgresql" to "PostgreSQL",
        "redis" to "Redis",
        "kubernetes" to "Kubernetes",
        "k8s" to "Kubernetes",
        "graphql" to "GraphQL",
        "github" to "GitHub",
        "gitlab" to "GitLab",
        "youtube" to "YouTube",
        "sqlite" to "SQLite",
        "mongodb" to "MongoDB",
        "wordpress" to "WordPress",
        "cypress" to "Cypress",
        "strapi" to "Strapi",
        "pytorch" to "PyTorch",
        "tensorflow" to "TensorFlow",
        "langchain" to "LangChain",
        "langgraph" to "LangGraph",

        // Fields of study & academic disciplines (ending in -s/ics)
        "physics" to "Physics",
        "economics" to "Economics",
        "macroeconomics" to "Macroeconomics",
        "microeconomics" to "Microeconomics",
        "econometrics" to "Econometrics",
        "mathematics" to "Mathematics",
        "maths" to "Maths",
        "statistics" to "Statistics",
        "robotics" to "Robotics",
        "analytics" to "Analytics",
        "genetics" to "Genetics",
        "mechanics" to "Mechanics",
        "linguistics" to "Linguistics",
        "acoustics" to "Acoustics",
        "ethics" to "Ethics",
        "politics" to "Politics",
        "optics" to "Optics",
        "thermodynamics" to "Thermodynamics",
        "aerodynamics" to "Aerodynamics",
        "cybernetics" to "Cybernetics",
        "informatics" to "Informatics",
        "bioinformatics" to "Bioinformatics",
        "geophysics" to "Geophysics",
        "astrophysics" to "Astrophysics",

        // General invariant nouns
        "series" to "Series",
        "species" to "Species",
        "news" to "News",
        "corpus" to "Corpus",
        "status" to "Status",
        "chaos" to "Chaos",
        "lens" to "Lens",
        "canvas" to "Canvas",
        "basis" to "Basis",
        "analysis" to "Analysis",
        "crisis" to "Crisis",
        "diagnosis" to "Diagnosis",
        "synopsis" to "Synopsis",
        "thesis" to "Thesis",
        "hypothesis" to "Hypothesis",
        "synthesis" to "Synthesis",
        "parenthesis" to "Parenthesis",
        "nexus" to "Nexus",
        "apparatus" to "Apparatus",
        "radius" to "Radius",
        "focus" to "Focus",
        "virus" to "Virus",
        "bonus" to "Bonus",
        "bias" to "Bias",
        "cross" to "Cross",
        "process" to "Process",
        "access" to "Access",
        "progress" to "Progress",
        "express" to "Express",
        "address" to "Address",
        "business" to "Business",
        "fitness" to "Fitness",
        "witness" to "Witness",
        "loss" to "Loss",
        "mass" to "Mass",
        "pass" to "Pass",
        "class" to "Class",
        "glass" to "Glass",
        "grass" to "Grass",
        "stress" to "Stress",
        "success" to "Success",
        "chess" to "Chess",
        "press" to "Press",
    )

    private val CANONICAL_ACRONYMS = mapOf(
        "ai" to "AI",
        "ml" to "ML",
        "llm" to "LLM",
        "llms" to "LLM",
        "nlp" to "NLP",
        "agi" to "AGI",
        "cli" to "CLI",
        "clis" to "CLI",
        "api" to "API",
        "apis" to "API",
        "sdk" to "SDK",
        "sdks" to "SDK",
        "ui" to "UI",
        "ux" to "UX",
        "kmp" to "KMP",
        "sql" to "SQL",
        "css" to "CSS",
        "html" to "HTML",
        "gpu" to "GPU",
        "gpus" to "GPU",
        "cpu" to "CPU",
        "cpus" to "CPU",
        "tpu" to "TPU",
        "tpus" to "TPU",
        "oss" to "OSS",
        "rest" to "REST",
        "json" to "JSON",
        "http" to "HTTP",
        "url" to "URL",
        "urls" to "URL",
        "pr" to "PR",
        "prs" to "PR",
        "ci/cd" to "CI/CD",
        "aws" to "AWS",
        "gcp" to "GCP",
        "wasm" to "WASM",
        "fts" to "FTS",
        "orm" to "ORM",
        "crud" to "CRUD",
        "saas" to "SaaS",
        "paas" to "PaaS",
    )

    private val IRREGULAR_PLURALS = mapOf(
        "people" to "Person",
        "children" to "Child",
        "men" to "Man",
        "women" to "Woman",
        "indices" to "Index",
        "indexes" to "Index",
        "matrices" to "Matrix",
        "analyses" to "Analysis",
        "theses" to "Thesis",
        "syntheses" to "Synthesis",
        "diagnoses" to "Diagnosis",
        "hypotheses" to "Hypothesis",
        "crises" to "Crisis",
        "criteria" to "Criterion",
        "data" to "Data",
        "media" to "Media",
        "movies" to "Movie",
        "zombies" to "Zombie",
        "cookies" to "Cookie",
        "rookies" to "Rookie",
        "selfies" to "Selfie",
        "calories" to "Calorie",
        "brownies" to "Brownie",
        "goodies" to "Goodie",
    )

    private val IRREGULAR_VES = mapOf(
        "leaves" to "Leaf",
        "knives" to "Knife",
        "lives" to "Life",
        "wolves" to "Wolf",
        "halves" to "Half",
        "shelves" to "Shelf",
        "thieves" to "Thief",
        "wives" to "Wife",
        "calves" to "Calf",
        "scarves" to "Scarf",
    )

    /**
     * Normalizes a raw tag string into a canonical, cleanly formatted tag.
     * Returns null if the tag is empty, invalid, or represents blacklisted format noise.
     */
    fun normalize(rawTag: String): String? {
        val trimmed = rawTag.trim()
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase()
        if (lower in FORMAT_NOISE_WORDS) return null

        // Check exact match in invariant terms
        INVARIANT_TERMS[lower]?.let { return it }

        // Split into words by spaces while keeping delimiters like '/' or '&'
        val words = trimmed.split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return null

        val normalizedWords = words.mapIndexed { index, word ->
            normalizeWord(
                word = word,
                isFirstWord = index == 0,
                isLastWord = index == words.lastIndex,
            )
        }

        val result = normalizedWords.joinToString(" ")
        return result.takeIf(String::isNotBlank)
    }

    /**
     * Produces a canonical normalized key used to group/snap plural, singular, and casing variants.
     */
    fun stem(tag: String): String {
        val normalized = normalize(tag) ?: tag.trim()
        return normalized.lowercase()
            .replace(Regex("[^a-z0-9+#.&]"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" ") { singularizeWord(it) }
    }

    private fun normalizeWord(word: String, isFirstWord: Boolean, isLastWord: Boolean): String {
        val lower = word.lowercase()

        // 1. Check direct acronym map
        CANONICAL_ACRONYMS[lower]?.let { return it }

        // 2. Check invariant terms map
        INVARIANT_TERMS[lower]?.let { return it }

        // 3. Check minor words if not the first word
        if (!isFirstWord && lower in MINOR_WORDS) {
            return lower
        }

        // 4. If this word contains special preserved casing (e.g. CamelCase like ClaudeCode or PascalCase), keep it
        if (word.drop(1).any(Char::isUpperCase) && !word.all(Char::isUpperCase)) {
            return word
        }

        // 5. Singularize/lemmatize the noun (primarily the last word in compound phrases, or single words)
        val singular = if (isLastWord || wordsAreConjunctive(word)) {
            singularizeWord(lower)
        } else {
            lower
        }

        // 6. Check acronym or invariant mapping again on singularized form
        CANONICAL_ACRONYMS[singular]?.let { return it }
        INVARIANT_TERMS[singular]?.let { return it }

        // 7. Standard title casing
        return singular.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun wordsAreConjunctive(word: String): Boolean =
        word.contains('&') || word.contains('/') || word.contains('-')

    /**
     * Converts a lowercase English word from plural to singular.
     */
    internal fun singularizeWord(lower: String): String {
        if (lower.length <= 3) return lower

        // Invariant check
        if (lower in INVARIANT_TERMS) return lower

        // Invariant academic disciplines ending in -ics / -physics / -economics
        if (isInvariantDiscipline(lower)) return lower

        // Irregular plurals
        IRREGULAR_PLURALS[lower]?.let { return it.lowercase() }
        IRREGULAR_VES[lower]?.let { return it.lowercase() }

        // Words ending in -ss do not drop s (class, glass, process, access, loss, pass, etc.)
        if (lower.endsWith("ss")) return lower

        // Suffix -ies
        if (lower.endsWith("ies")) {
            val beforeIes = lower.dropLast(3)
            return beforeIes + "y"
        }

        // Suffix -ves (regular non-irregular)
        if (lower.endsWith("ves")) {
            // waves -> wave, drives -> drive, perspectives -> perspective
            return lower.dropLast(1)
        }

        // Suffix -sses, -shes, -ches, -xes, -zes
        if (lower.endsWith("sses") || lower.endsWith("shes") || lower.endsWith("ches") ||
            lower.endsWith("xes") || lower.endsWith("zes")
        ) {
            // classes -> class, dishes -> dish, matches -> match, boxes -> box, quizzes -> quiz
            return if (lower.endsWith("zzes")) lower.dropLast(3) else lower.dropLast(2)
        }

        // Suffix -oes (heroes -> hero, tomatoes -> tomato)
        if (lower.endsWith("oes")) {
            return lower.dropLast(2)
        }

        // Suffix -es for words ending in silent 'e' + 's' (recipes -> recipe, guidelines -> guideline, scenes -> scene)
        if (lower.endsWith("es")) {
            return lower.dropLast(1)
        }

        // Suffix -s for regular words (agents -> agent, models -> model, tools -> tool, plants -> plant)
        if (lower.endsWith("s") && !lower.endsWith("us") && !lower.endsWith("is") && !lower.endsWith("as")) {
            return lower.dropLast(1)
        }

        return lower
    }

    private fun isInvariantDiscipline(lower: String): Boolean =
        lower.endsWith("economics") || lower.endsWith("physics") || lower.endsWith("statistics") ||
            lower.endsWith("robotics") || lower.endsWith("analytics") || lower.endsWith("mechanics") ||
            lower.endsWith("linguistics") || lower.endsWith("optics") || lower.endsWith("informatics") ||
            lower.endsWith("acoustics") || lower.endsWith("genetics") || lower.endsWith("thermodynamics") ||
            lower.endsWith("aerodynamics") || lower.endsWith("cybernetics")
}
