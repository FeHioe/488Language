package compiler488.semantics;

import compiler488.ast.Locatable;

/**
 * Exception subclass for reporting semantic errors.
 *
 * @author James Yuan
 */
class SemanticErrorException extends Exception
{
    public SemanticErrorException(int actionNumber, Locatable node)
    {
        super("Semantic action " + String.valueOf(actionNumber) +
              " failed at line: " + String.valueOf(node.getLineNum()) +
              " column: " + String.valueOf(node.getColNum()));
    }

}
