package com.ezymusy.app.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException
import java.net.SocketTimeoutException

class ClassifyTest {

    @Test
    fun `deleted or private video wrapped by the loader is unavailable`() {
        assertEquals(Failure.UNAVAILABLE, classify(IOException(ContentNotAvailableException("This video is private"))))
    }

    @Test
    fun `parse failure is a broken extractor, not a dead track`() {
        assertEquals(Failure.BROKEN, classify(IOException(ParsingException("Could not get player"))))
    }

    @Test
    fun `rate limiting and connection problems never mark or skip`() {
        assertEquals(Failure.NETWORK, classify(IOException(ReCaptchaException("reCaptcha", "https://youtube.com"))))
        assertEquals(Failure.NETWORK, classify(SignInConfirmNotBotException("Sign in to confirm you're not a bot")))
        assertEquals(Failure.NETWORK, classify(SocketTimeoutException()))
    }
}
