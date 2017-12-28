package compiler488.ast.decl;

/**
 * Holds the declaration part of an array.
 */
public class ArrayDeclPart extends DeclarationPart
{
    /* The lower and upper boundaries of the array. */
    private Integer lb, ub;

    /* The number of objects the array holds. */
    private Integer size;

    /* Store whether the declaration included a lower bound. */
    private boolean declaredLowerBound = false;

    /**
     * Returns a string that describes the array.
     */
    @Override
    public String toString()
    {
        return name + "[" + lb + ".." + ub + "]";
    }

    public Integer getLowerBoundary()
    {
        return this.lb;
    }

    public void setLowerBoundary(Integer lb)
    {
        this.lb = lb;
    }

    public Integer getUpperBoundary()
    {
        return this.ub;
    }

    public void setUpperBoundary(Integer ub)
    {
        this.ub = ub;
    }

    public boolean hasDeclaredLowerBound()
    {
        return this.declaredLowerBound;
    }

    public void setDeclaredLowerBound(boolean declaredLowerBound)
    {
        this.declaredLowerBound = declaredLowerBound;
    }

    public Integer getSize()
    {
        return this.size;
    }

    public void setSize(Integer size)
    {
        this.size = size;
    }
}
