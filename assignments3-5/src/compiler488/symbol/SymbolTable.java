package compiler488.symbol;

import java.util.HashMap;

import compiler488.ast.AST;
import compiler488.ast.decl.*;
import compiler488.ast.stmt.Scope;
import compiler488.symbol.type.ArraySymbolType;

/**
 * Symbol Table
 * This almost empty class is a framework for implementing
 * a Symbol Table class for the CSC488S compiler
 * <p>
 * Each implementation can change/modify/delete this class
 * as they see fit.
 *
 * @author  <B>Dawing Cho</B>
 * @author  <B>Felicia Hoie</B>
 * @author  <B>Ishtiaque Khaled</B>
 * @author  <B>Sunny Li</B>
 * @author  <B>James Yuan</B>
 */

public class SymbolTable
{
    // Symbols in current table
    private HashMap<String, SymbolTableEntry> symbols = new HashMap<>();
    // Child scopes of current table
    private HashMap<Scope, SymbolTable> childScopes = new HashMap<>();

    private SymbolTable parentScope;
    private short scope = 0;

    // Initial order number is the lowest order number of any symbol in this table.
    // This is always 0 for major scopes and may be 0 or non-zero in minor scopes.
    private short initialOrderNum = 0;
    private short currentOrderNum = 0;

    /**
     * String used by Main to print symbol table
     * version information.
     */
    public final static String version = "Winter 2017";

    /**
     * Symbol Table  constructor
     * Create and initialize a symbol table
     */
    public SymbolTable() {}

    public boolean addEntry(SymbolTableEntry entry)
    {
        if (this.symbols.containsKey(entry.getIdent())) return false;

        this.symbols.put(entry.getIdent(), entry);
        entry.enclosingTable = this;

        if (entry.getType() instanceof ArraySymbolType)
        {
            ArraySymbolType as = (ArraySymbolType)entry.getType();
            entry.laddr = this.getNextLogicalAddress(as.getUpperBound() - as.getLowerBound() + 1);

        } else if (!(entry.getVal() instanceof RoutineDecl))
        {
            entry.laddr = this.getNextLogicalAddress(1);
        } else
        {
            entry.laddr = new LogicalAddress((short)(this.scope + 1), (short)0);
        }

        return true;
    }

    /* Get an entry in the symbol table if it exists. Also search parent scopes. */
    public SymbolTableEntry getEntry(String ident)
    {
        SymbolTableEntry entry;
        SymbolTable curr = this;

        do
        {
            entry = curr.symbols.get(ident);
            curr = curr.parentScope;
        } while (entry == null && curr != null);

        return entry;
    }

    public SymbolTable getParentScope()
    {
        return this.parentScope;
    }

    public void setParentScope(SymbolTable parentScope, boolean isMinorScope)
    {
        this.parentScope = parentScope;
        if (isMinorScope)
        {
            // Minor scopes do not increase the scope level so the order number
            // must continue from where the parent symbol table left off.
            this.currentOrderNum = parentScope.currentOrderNum;
            this.initialOrderNum = parentScope.currentOrderNum;
            this.scope = parentScope.scope;
        }
        else this.scope = (short)(parentScope.scope + 1);
    }

    public short getScope()
    {
        return this.scope;
    }

    public SymbolTable addChildScope(Scope node, SymbolTable table, boolean isMinorScope)
    {
        if (isMinorScope)
        {
            // Minor scopes do not increase the scope level so the order number
            // must continue from where the parent symbol table left off.
            table.currentOrderNum = this.currentOrderNum;
            table.initialOrderNum = this.currentOrderNum;
            table.scope = this.scope;
        } else
        {
            table.scope = (short)(this.scope + 1);
        }

        table.parentScope = this;
        this.childScopes.put(node, table);
        return table;
    }

    public SymbolTable getChildScope(Scope node)
    {
        return this.childScopes.get(node);
    }

    public short getInitialOrderNum()
    {
        return this.initialOrderNum;
    }

    private LogicalAddress getNextLogicalAddress(int size)
    {
        LogicalAddress la = new LogicalAddress(this.scope, this.currentOrderNum);
        this.currentOrderNum += size;
        return la;
    }
}
