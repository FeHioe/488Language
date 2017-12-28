package compiler488.visitor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import compiler488.ast.stmt.Program;
import compiler488.symbol.SymbolTable;

public class VisitorBase
{
    /**
     * Global symbol table.
     */
    protected SymbolTable globalSymTable;

    public void setGlobalSymTable(SymbolTable globalSymTable)
    {
        this.globalSymTable = globalSymTable;
    }

    public SymbolTable getGlobalSymTable()
    {
        return this.globalSymTable;
    }

    public void doTraversal(Program node) {}

    protected void visit(Object node)
    {
        this.dispatch(node);
    }

    protected void dispatch(Object node)
    {
        // Find the method that has a parameter type that most specifically
        // matches the runtime type of node.
        Method m = null;

        Class curr = node.getClass();
        do
        {
            try
            {
                m = this.getClass().getDeclaredMethod("visit", curr);
            } catch (NoSuchMethodException nsme)
            {
                curr = curr.getSuperclass();
            }

        } while (m == null && curr != Object.class);

        if (m == null)
        {
            // Check the interfaces if nothing is found in the class heirarchy.
            Class[] interfaces = node.getClass().getInterfaces();
            for (int i = 0; m == null && i < interfaces.length; ++i)
            {
                try
                {
                    m = this.getClass().getDeclaredMethod("visit", interfaces[i]);
                } catch (NoSuchMethodException nsme)
                {
                    // Nothing to do here.
                }
            }
        }

        if (curr == Object.class || m == null)
        {
            defaultVisit(node);
            return;
        }

        try
        {
            m.invoke(this, node);
        } catch (IllegalAccessException e)
        {
            defaultVisit(node);
        } catch (InvocationTargetException e2)
        {
            if (e2.getCause() instanceof RuntimeException) throw (RuntimeException)e2.getCause();
            else throw new RuntimeException(e2.getCause());
        }
    }

    private void defaultVisit(Object node) {}
}
