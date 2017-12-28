package compiler488.ast.expn;

import compiler488.ast.Readable;
import compiler488.symbol.Kind;

/**
 * References to a scalar variable.
 */
public class IdentExpn extends Expn implements Readable
{
    private String ident;    // name of the identifier

    private Kind kind = Kind.UNKNOWN;

    /**
     * Returns the name of the variable or function.
     */
    @Override
    public String toString()
    {
        return ident;
    }

    public String getIdent()
    {
        return ident;
    }

    public void setIdent(String ident)
    {
        this.ident = ident;
    }

    public Kind getKind()
    {
        return this.kind;
    }

    public void setKind(Kind kind)
    {
        this.kind = kind;
    }
}
