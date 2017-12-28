package compiler488.codegen;

/**
 * Exception subclass for reporting codegen errors.
 *
 * @author James Yuan
 */
class CodeGenErrorException extends RuntimeException
{
    CodeGenErrorException(String message)
    {
        super("Failed codegen with error: " + message);
    }
}
