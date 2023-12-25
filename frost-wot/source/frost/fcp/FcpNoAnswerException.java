package frost.fcp;

/**
 * Signifies that your FCP request received no answer from the Freenet node.
 * This is caused by a bug in Freenet, which sometimes fails to answer any
 * socket queries, so that the read() simply times out instead.
 * However, this exception is not thrown by any of Frost's FCP libraries.
 * Instead, it's optional and usable inside of the libraries, to signify
 * this special state. For example, to re-try the message with a new connection
 * to attempt to work around the bug.
 */
public class FcpNoAnswerException extends FcpToolsException {
    public FcpNoAnswerException()
    {
        super("No FCP answer from Freenet node.");
    }
}
