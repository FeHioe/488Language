package compiler488.ast.decl;

import compiler488.ast.AST;
import compiler488.symbol.SymbolTableEntry;

/**
 * The common features of declarations' parts.
 */
public class DeclarationPart extends AST
{

    /**
     * The name of the thing being declared.
     */
    protected String name;

    /**
     * The associated symbol table entry.
     */
    private SymbolTableEntry entry;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public SymbolTableEntry getEntry()
    {
        return this.entry;
    }

    public void setEntry(SymbolTableEntry entry)
    {
        this.entry = entry;
    }
}
