package com.ezymusy.app.feature.library

import com.ezymusy.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.UnknownHostException

class AddErrorTest {

    @Test
    fun `only connection problems blame the connection`() {
        assertEquals(R.string.error_add_failed, addError(UnknownHostException()))
        assertEquals(R.string.error_add_failed, addError(ReCaptchaException("reCaptcha", "https://youtube.com")))
    }

    @Test
    fun `private links say so`() {
        assertEquals(R.string.error_add_unavailable, addError(ContentNotAvailableException("This playlist is private")))
    }

    @Test
    fun `parse failures and unknown crashes are unreadable, not network`() {
        assertEquals(R.string.error_add_unreadable, addError(ParsingException("Could not get playlist")))
        // What R8 stripping protobuf fields looked like in 0.1.1.
        assertEquals(R.string.error_add_unreadable, addError(RuntimeException("Field browseId_ for ya2 not found")))
    }
}
