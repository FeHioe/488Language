package compiler488.ast;

public interface Locatable
{
    /*
     * Classes that extend this interface have a line and column number.
     */
    void setFilePosition(int lineNum, int colNum);

    int getLineNum();

    int getColNum();
}
