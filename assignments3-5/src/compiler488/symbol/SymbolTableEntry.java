package compiler488.symbol;

import compiler488.ast.decl.ArrayDeclPart;
import compiler488.ast.decl.RoutineDecl;
import compiler488.ast.decl.ScalarDecl;
import compiler488.ast.decl.ScalarDeclPart;
import compiler488.ast.type.BooleanType;
import compiler488.ast.type.IntegerType;
import compiler488.ast.type.Type;
import compiler488.symbol.type.*;
import compiler488.ast.AST;

import java.util.ArrayList;

public class SymbolTableEntry
{
    // Built-in types.
    public static final BooleanSymbolType BOOLEAN_SYMBOL_TYPE = new BooleanSymbolType();
    public static final IntegerSymbolType INTEGER_SYMBOL_TYPE = new IntegerSymbolType();

    private final String ident;
    private final Kind kind;
    private final AST val;
    private SymbolType type;
    private ArrayList<SymbolTableEntry> relatedSymbols = new ArrayList<>();

    /* Symbol table will update these when this entry is added to it. */
    SymbolTable enclosingTable;
    LogicalAddress laddr;

    public SymbolTableEntry(RoutineDecl node)
    {
        Kind k = node.getType() != null ? Kind.FUNC : Kind.PROC;
        CallableSymbolType type = new CallableSymbolType(convertType(node.getType()), 0);

        this.ident = node.getName();
        this.type = type;
        this.kind = k;
        this.val = node;
    }
    /* Declare a new scalar variable. */
    public SymbolTableEntry(ScalarDeclPart node)
    {
        this.ident = node.getName();
        this.kind = Kind.VAR;
        this.val = node;
    }

    /* Declare a new parameter. */
    public SymbolTableEntry(ScalarDecl node)
    {
        this.ident = node.getName();
        this.type = SymbolTableEntry.convertType(node.getType());
        this.kind = Kind.PARAM;
        this.val = node;
    }

    /* Declare a new array. */
    public SymbolTableEntry(ArrayDeclPart node)
    {
        ArraySymbolType type = new ArraySymbolType(node.getLowerBoundary().shortValue(),
                                                   node.getUpperBoundary().shortValue(),
                                                   new ScalarSymbolType());

        this.ident = node.getName();
        this.type = type;
        this.kind = Kind.VAR;
        this.val = node;
    }

    public String getIdent()
    {
        return this.ident;
    }

    public Kind getKind()
    {
        return this.kind;
    }

    public AST getVal()
    {
        return this.val;
    }

    public void setType(SymbolType type)
    {
        this.type = type;
    }

    public SymbolType getType()
    {
        return this.type;
    }

    public ArrayList<SymbolTableEntry> getRelatedSymbols()
    {
        return this.relatedSymbols;
    }

    public LogicalAddress getLaddr()
    {
        return this.laddr;
    }

    public static SymbolType convertType(Type type)
    {
        if (type instanceof BooleanType) return BOOLEAN_SYMBOL_TYPE;
        if (type instanceof IntegerType) return INTEGER_SYMBOL_TYPE;
        return null;
    }
}
