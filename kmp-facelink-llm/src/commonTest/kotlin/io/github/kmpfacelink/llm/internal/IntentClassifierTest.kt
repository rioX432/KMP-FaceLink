package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import kotlin.test.Test
import kotlin.test.assertEquals

class IntentClassifierTest {

    private val classifier = IntentClassifier()

    @Test
    fun shortGreetingClassifiesAsSimple() {
        val history = ConversationHistory()
        history.addUser("Hi there")
        assertEquals(QueryComplexity.SIMPLE, classifier.classify(history))
    }

    @Test
    fun simpleKeywordClassifiesAsSimple() {
        val history = ConversationHistory()
        history.addUser("What's the weather like?")
        assertEquals(QueryComplexity.SIMPLE, classifier.classify(history))
    }

    @Test
    fun longAnalyticalQuestionClassifiesAsComplex() {
        val history = ConversationHistory()
        history.addUser(
            "Can you explain the differences between transformer architectures " +
                "and how attention mechanisms work in modern large language models?"
        )
        assertEquals(QueryComplexity.COMPLEX, classifier.classify(history))
    }

    @Test
    fun complexKeywordClassifiesAsComplex() {
        val history = ConversationHistory()
        history.addUser("Please analyze this data and compare the results")
        assertEquals(QueryComplexity.COMPLEX, classifier.classify(history))
    }

    @Test
    fun moderateQuestionClassifiesAsModerate() {
        val history = ConversationHistory()
        history.addUser("What are the main features of Kotlin Multiplatform?")
        assertEquals(QueryComplexity.MODERATE, classifier.classify(history))
    }

    @Test
    fun emptyHistoryClassifiesAsSimple() {
        val history = ConversationHistory()
        assertEquals(QueryComplexity.SIMPLE, classifier.classify(history))
    }

    @Test
    fun deepConversationWithLongMessageClassifiesAsComplex() {
        val history = ConversationHistory()
        // Build up conversation depth > 10
        repeat(6) {
            history.addUser("Question $it about the topic at hand")
            history.addAssistant("Response $it with details")
        }
        history.addUser(
            "Given everything we discussed, can you provide a detailed summary " +
                "of all the points we covered and how they connect to each other?"
        )
        assertEquals(QueryComplexity.COMPLEX, classifier.classify(history))
    }

    @Test
    fun onlyAssistantMessagesClassifiesAsSimple() {
        val history = ConversationHistory()
        history.addAssistant("I can help you with that.")
        assertEquals(QueryComplexity.SIMPLE, classifier.classify(history))
    }
}
