package compiler488.ast;

/**
 * This is a placeholder at the top of the Abstract Syntax Tree hierarchy. It is
 * a convenient place to add common behaviour.
 *
 * @author Dave Wortman, Marsha Chechik, Danny House
 */
public class AST implements Locatable
{
    public final static String version = "Winter 2017";

    private int lineNum = -1;
    private int colNum = -1;

    public void setFilePosition(int lineNum, int colNum) {
        // lineNum and colNum are 0 indexed but we want to report
        // them starting at 1.
        this.lineNum = lineNum + 1;
        this.colNum = colNum + 1;
    }

    public int getLineNum() {
        return this.lineNum;
    }

    public int getColNum() {
        return this.colNum;
    }
}
