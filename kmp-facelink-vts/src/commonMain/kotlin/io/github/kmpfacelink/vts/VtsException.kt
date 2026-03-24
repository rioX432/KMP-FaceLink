package io.github.kmpfacelink.vts

/**
 * Exception thrown when VTube Studio API returns an error response.
 *
 * @property errorId VTS error ID from the API response
 * @property vtsMessage Error message from VTS
 */
public class VtsApiException(
    public val errorId: Int,
    public val vtsMessage: String,
) : Exception("VTS API error $errorId: $vtsMessage")

/**
 * Exception thrown when VTS authentication fails.
 *
 * @property reason Reason for authentication failure from VTS
 */
public class VtsAuthException(
    public val reason: String,
) : Exception("VTS authentication failed: $reason")
