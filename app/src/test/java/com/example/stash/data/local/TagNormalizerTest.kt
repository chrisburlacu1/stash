package com.example.stash.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagNormalizerTest {

    @Test
    fun `lemmatizes regular and irregular plurals to singular`() {
        // Regular plurals ending in -s
        assertEquals("AI Agent", TagNormalizer.normalize("AI Agents"))
        assertEquals("Machine Learning Model", TagNormalizer.normalize("machine learning models"))
        assertEquals("Prompt", TagNormalizer.normalize("Prompts"))
        assertEquals("Tool", TagNormalizer.normalize("Tools"))
        assertEquals("Workflow", TagNormalizer.normalize("Workflows"))
        assertEquals("Token", TagNormalizer.normalize("Tokens"))
        assertEquals("Dataset", TagNormalizer.normalize("Datasets"))

        // Plurals ending in -ies -> -y
        assertEquals("Strategy", TagNormalizer.normalize("Strategies"))
        assertEquals("Story", TagNormalizer.normalize("Stories"))
        assertEquals("Library", TagNormalizer.normalize("Libraries"))
        assertEquals("Technology", TagNormalizer.normalize("Technologies"))
        assertEquals("Category", TagNormalizer.normalize("Categories"))
        assertEquals("Commodity", TagNormalizer.normalize("Commodities"))

        // Plurals ending in vowel + ies -> ie
        assertEquals("Movie", TagNormalizer.normalize("Movies"))
        assertEquals("Zombie", TagNormalizer.normalize("Zombies"))

        // Plurals ending in -es with silent e
        assertEquals("Recipe", TagNormalizer.normalize("Recipes"))
        assertEquals("Guideline", TagNormalizer.normalize("Guidelines"))
        assertEquals("Pipeline", TagNormalizer.normalize("Pipelines"))
        assertEquals("Scene", TagNormalizer.normalize("Scenes"))
        assertEquals("Technique", TagNormalizer.normalize("Techniques"))

        // Plurals ending in sibilant -es (-sses, -shes, -ches, -xes, -zes)
        assertEquals("Class", TagNormalizer.normalize("Classes"))
        assertEquals("Dish", TagNormalizer.normalize("Dishes"))
        assertEquals("Match", TagNormalizer.normalize("Matches"))
        assertEquals("Box", TagNormalizer.normalize("Boxes"))
        assertEquals("Quiz", TagNormalizer.normalize("Quizzes"))

        // Irregular plurals
        assertEquals("Index", TagNormalizer.normalize("Indices"))
        assertEquals("Matrix", TagNormalizer.normalize("Matrices"))
        assertEquals("Analysis", TagNormalizer.normalize("Analyses"))
        assertEquals("Thesis", TagNormalizer.normalize("Theses"))
        assertEquals("Criterion", TagNormalizer.normalize("Criteria"))
        assertEquals("Person", TagNormalizer.normalize("People"))
        assertEquals("Leaf", TagNormalizer.normalize("Leaves"))
        assertEquals("Knife", TagNormalizer.normalize("Knives"))
    }

    @Test
    fun `protects invariant nouns and academic fields ending in s`() {
        assertEquals("iOS", TagNormalizer.normalize("ios"))
        assertEquals("macOS", TagNormalizer.normalize("macos"))
        assertEquals("DevOps", TagNormalizer.normalize("devops"))
        assertEquals("MLOps", TagNormalizer.normalize("mlops"))
        assertEquals("Node.js", TagNormalizer.normalize("node.js"))
        assertEquals("Vue.js", TagNormalizer.normalize("vue.js"))
        assertEquals("Next.js", TagNormalizer.normalize("next.js"))
        assertEquals("Postgres", TagNormalizer.normalize("postgres"))
        assertEquals("PostgreSQL", TagNormalizer.normalize("postgresql"))
        assertEquals("Redis", TagNormalizer.normalize("redis"))
        assertEquals("Kubernetes", TagNormalizer.normalize("kubernetes"))
        assertEquals("Kubernetes", TagNormalizer.normalize("k8s"))

        // Academic fields
        assertEquals("Physics", TagNormalizer.normalize("physics"))
        assertEquals("Economics", TagNormalizer.normalize("economics"))
        assertEquals("Mathematics", TagNormalizer.normalize("mathematics"))
        assertEquals("Statistics", TagNormalizer.normalize("statistics"))
        assertEquals("Robotics", TagNormalizer.normalize("robotics"))
        assertEquals("Analytics", TagNormalizer.normalize("analytics"))
        assertEquals("Genetics", TagNormalizer.normalize("genetics"))

        // General invariant nouns
        assertEquals("Series", TagNormalizer.normalize("series"))
        assertEquals("Species", TagNormalizer.normalize("species"))
        assertEquals("News", TagNormalizer.normalize("news"))
        assertEquals("Status", TagNormalizer.normalize("status"))
        assertEquals("Lens", TagNormalizer.normalize("lens"))
        assertEquals("Process", TagNormalizer.normalize("process"))
        assertEquals("Access", TagNormalizer.normalize("access"))
        assertEquals("Business", TagNormalizer.normalize("business"))
    }

    @Test
    fun `formats acronyms and compound tags properly`() {
        assertEquals("CLI", TagNormalizer.normalize("cli"))
        assertEquals("API", TagNormalizer.normalize("api"))
        assertEquals("SDK", TagNormalizer.normalize("sdk"))
        assertEquals("UI", TagNormalizer.normalize("ui"))
        assertEquals("UX", TagNormalizer.normalize("ux"))
        assertEquals("KMP", TagNormalizer.normalize("kmp"))
        assertEquals("SQL", TagNormalizer.normalize("sql"))
        assertEquals("CI/CD", TagNormalizer.normalize("ci/cd"))
        assertEquals("GraphQL", TagNormalizer.normalize("graphql"))
        assertEquals("PyTorch", TagNormalizer.normalize("pytorch"))
        assertEquals("TensorFlow", TagNormalizer.normalize("tensorflow"))
        assertEquals("LangChain", TagNormalizer.normalize("langchain"))
        assertEquals("LangGraph", TagNormalizer.normalize("langgraph"))
    }

    @Test
    fun `normalizes open domain terms across film, culinary, gardening, finance`() {
        // Film & Screenwriting
        assertEquals("Film & Cinema", TagNormalizer.normalize("film & cinema"))
        assertEquals("Screenwriting", TagNormalizer.normalize("screenwriting"))
        assertEquals("Screenplay", TagNormalizer.normalize("screenplays"))
        assertEquals("Scene Transition", TagNormalizer.normalize("scene transitions"))
        assertEquals("Character Arc", TagNormalizer.normalize("character arcs"))

        // Culinary & Cooking
        assertEquals("Culinary", TagNormalizer.normalize("culinary"))
        assertEquals("Sourdough Starter", TagNormalizer.normalize("sourdough starters"))
        assertEquals("Fermentation", TagNormalizer.normalize("fermentation"))
        assertEquals("Bread Baking", TagNormalizer.normalize("bread baking"))

        // Finance & Economics
        assertEquals("Value Investing", TagNormalizer.normalize("value investing"))
        assertEquals("Index Fund", TagNormalizer.normalize("index funds"))
        assertEquals("Asset Allocation", TagNormalizer.normalize("asset allocations"))
        assertEquals("Macroeconomics", TagNormalizer.normalize("macroeconomics"))

        // Gardening
        assertEquals("Gardening", TagNormalizer.normalize("gardening"))
        assertEquals("Soil Health", TagNormalizer.normalize("soil health"))
        assertEquals("Perennial Plant", TagNormalizer.normalize("perennial plants"))
        assertEquals("Companion Planting", TagNormalizer.normalize("companion planting"))
    }

    @Test
    fun `filters out blacklisted format and medium noise words`() {
        assertNull(TagNormalizer.normalize("podcast"))
        assertNull(TagNormalizer.normalize("Podcasts"))
        assertNull(TagNormalizer.normalize("Audio"))
        assertNull(TagNormalizer.normalize("Episode"))
        assertNull(TagNormalizer.normalize("Episodes"))
        assertNull(TagNormalizer.normalize("Article"))
        assertNull(TagNormalizer.normalize("Articles"))
        assertNull(TagNormalizer.normalize("Video"))
        assertNull(TagNormalizer.normalize("Videos"))
        assertNull(TagNormalizer.normalize("Newsletter"))
        assertNull(TagNormalizer.normalize("Post"))
        assertNull(TagNormalizer.normalize("Website"))
        assertNull(TagNormalizer.normalize("Webpage"))
        assertNull(TagNormalizer.normalize("Tutorial"))
        assertNull(TagNormalizer.normalize("Documentation"))
        assertNull(TagNormalizer.normalize("   "))
        assertNull(TagNormalizer.normalize(""))
    }

    @Test
    fun `stem computes matching keys for fuzzy snapping`() {
        assertEquals("ai agent", TagNormalizer.stem("AI Agents"))
        assertEquals("ai agent", TagNormalizer.stem("ai-agent"))
        assertEquals("ai agent", TagNormalizer.stem("Ai Agent"))
        assertEquals("screenplay", TagNormalizer.stem("Screenplays"))
        assertEquals("screenplay", TagNormalizer.stem("Screenplay"))
        assertEquals("recipe", TagNormalizer.stem("Recipes"))
        assertEquals("recipe", TagNormalizer.stem("Recipe"))
        assertEquals("devops", TagNormalizer.stem("DevOps"))
        assertEquals("physics", TagNormalizer.stem("Physics"))
    }
}
