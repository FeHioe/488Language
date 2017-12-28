package compiler488.semantics;

import java.io.*;
import java.util.*;

import compiler488.ast.*;
import compiler488.ast.Readable;
import compiler488.ast.decl.*;
import compiler488.ast.expn.*;
import compiler488.ast.stmt.*;
import compiler488.symbol.*;
import compiler488.symbol.type.*;
import compiler488.visitor.VisitorBase;

/**
 * Implement semantic analysis for compiler 488
 *
 * @author  <B>Dawing Cho</B>
 * @author  <B>Felicia Hoie</B>
 * @author  <B>Ishtiaque Khaled</B>
 * @author  <B>Sunny Li</B>
 * @author  <B>James Yuan</B>
 */
public class Semantics extends VisitorBase
{
    public static final String version = "Winter 2017";

    /**
     * flag for tracing semantic analysis
     */
    private boolean traceSemantics = false;
    /**
     * file sink for semantic analysis trace
     */
    private String traceFile;

    /**
     * Stack for keeping track of the current active symbol tables.
     */
    private Stack<SymbolTable> symTablesStack = new Stack<>();

    /**
     * Stack for keeping track of loops the AST traversal is currently inside.
     */
    private Stack<LoopingStmt> loopStack = new Stack<>();

    /**
     * Stack for keeping track of the routine declaration the AST traversal is currently inside.
     */
    private Stack<RoutineDecl> routineStack = new Stack<>();

    /**
     * List of semantic errors that occurred during the AST traversal.
     */
    private ArrayList<SemanticErrorException> semanticErrors = new ArrayList<>();

    /**
     * SemanticAnalyzer constructor
     */
    public Semantics(boolean traceSemantic, String traceFile)
    {
        this.traceSemantics = traceSemantic;
        this.traceFile = traceFile;
    }

    public void doTraversal(Program node)
    {
        this.visit(node);

        // Print out all semantic error messages.
        for (SemanticErrorException se : this.semanticErrors)
        {
            System.err.println(se.getMessage());
        }
    }

    /*
     *  ========================================================
     *  Overload a visit method for each AST node type below.
     *  ========================================================
     */

    public void visit(Program node)
    {
        this.semanticAction00(node);

        // Want to call visit as a scope and not as a Program.
        this.visit((Scope)node);

        this.semanticAction01();
    }

    public void visit(AssignStmt node)
    {
        if (node.getLval() instanceof IdentExpn) ((IdentExpn)node.getLval()).setKind(Kind.VAR);

        this.dispatch(node.getLval());
        this.dispatch(node.getRval());

        this.semanticAction34(node.getLval(), node.getRval());
    }

    public void visit(IfStmt node)
    {
        this.dispatch(node.getCondition());
        this.semanticAction30(node.getCondition());

        this.dispatch(node.getWhenTrue());
        if (node.getWhenFalse() != null) this.dispatch(node.getWhenFalse());
    }

    public void visit(LoopingStmt node)
    {
        this.dispatch(node.getExpn());
        this.semanticAction30(node.getExpn());

        this.loopStack.push(node);
        this.dispatch(node.getBody());
        this.loopStack.pop();
    }

    public void visit(ExitStmt node)
    {
        this.semanticAction50(node);

        if (node.hasExitWithLevel()) this.semanticAction53(node);

        if (node.getExpn() != null)
        {
            this.dispatch(node.getExpn());
            this.semanticAction30(node.getExpn());
        }
    }

    public void visit(ReturnStmt node)
    {
        if (node.getValue() != null)
        {
            this.dispatch(node.getValue());
            if (this.semanticAction51(node))
            {
                // It doesn't make sense to do S35 when S51 fails.
                this.semanticAction35(node.getValue());
            }
        } else
        {
            this.semanticAction52(node);
        }
    }

    public void visit(WriteStmt node)
    {
        LinkedList<Printable> printables = node.getOutputs().getNodes();
        for (Printable p : printables)
        {
            // Don't dispatch here as we do want it to call the visit(Printable node) method.
            this.visit(p);
        }
    }

    public void visit(ReadStmt node)
    {
        LinkedList<Readable> readables = node.getInputs().getNodes();
        for (Readable r : readables)
        {
            // Don't dispatch here as we do want it to call the visit(Readable node) method.
            this.visit(r);
        }
    }

    public void visit(ProcedureCallStmt node)
    {
        // It doesn't make sense to do any of the following checks if this one fails.
        if (!this.semanticAction41(node.getName(), node, true)) return;

        if (node.getArguments() != null)
        {
            // It doesn't make sense to perform the other semantic checks if the number
            // of arguments doesn't match the number of formal parameters.
            if (!this.semanticAction43(node)) return;

            SymbolTableEntry entry = this.symTablesStack.peek().getEntry(node.getName());
            this.checkArguments(node.getArguments(), entry);
        } else
        {
            this.semanticAction42(node);
        }
    }

    public void visit(MultiDeclarations node)
    {
        LinkedList<DeclarationPart> dps = node.getElements().getNodes();
        for (DeclarationPart dp : dps)
        {
            this.dispatch(dp);
        }
        this.semanticAction47(node);
    }

    public void visit(RoutineDecl node)
    {
        this.routineStack.push(node);

        // We won't do the semantic checks in the same order as
        // semantic.pdf specifies as it's difficult to implement.
        // This should be functionally equivalent though.

        boolean isProc = node.getType() == null;
        boolean hasParams = node.getRoutineBody().getParameters() != null;

        // 1) Open the scope for the routine.
        if (isProc) this.semanticAction08(node);
        else this.semanticAction04(node, 4);

        // 2) Process parameters if any exist.
        if (hasParams)
        {
            this.semanticAction14();
            LinkedList<ScalarDecl> params = node.getRoutineBody().getParameters().getNodes();
            for (ScalarDecl s : params) this.dispatch(s);
        }

        // 3) Declare the routine.
        if (isProc && hasParams) this.semanticAction18(node);
        else if (isProc) this.semanticAction17(node);
        else if (hasParams) this.semanticAction12(node);
        else this.semanticAction11(node, 11);

        // 4) Visit the routine's body scope.
        this.dispatch(node.getRoutineBody().getBody());

        // 5) Perform S13 before the routine's symbol table is popped off the stack.
        this.semanticAction13();

        // 6) Close the routine's scope.
        if (isProc) this.semanticAction09();
        else this.semanticAction05(5);

        this.routineStack.pop();
    }

    public void visit(ScalarDeclPart node)
    {
        this.semanticAction10(node);
    }

    public void visit(ArrayDeclPart node)
    {
        if (node.hasDeclaredLowerBound())
        {
            this.semanticAction46(node);
            this.semanticAction19(node, 19);
        } else
        {
            this.semanticAction48(node);
        }
    }

    public void visit(Scope node)
    {
        // The symbol table for routine and program scopes have already been
        // constructed. Only minor scopes don't have a symbol when this
        // method is called.
        boolean isMinorScope = node.getSymTable() == null;
        if (isMinorScope) this.semanticAction06(node);

        LinkedList<Declaration> declarations = node.getDeclarations().getNodes();
        for (Declaration d : declarations) this.dispatch(d);

        this.semanticAction02();

        LinkedList<Stmt> statements = node.getStatements().getNodes();
        for (Stmt s : statements) this.dispatch(s);

        if (isMinorScope) this.semanticAction07();
    }

    public void visit(Printable node)
    {
        this.dispatch(node);
        if (!(node instanceof SkipConstExpn || node instanceof TextConstExpn))
        {
            this.semanticAction31((Expn)node);
        }
    }

    public void visit(Readable node)
    {
        this.dispatch(node);
        this.semanticAction31((Expn)node);
    }

    public void visit(ScalarDecl node)
    {
        this.semanticAction16();
        this.semanticAction15(node);
        this.semanticAction39(node.getName(), node, true);
    }

    public void visit(IdentExpn node)
    {
        // An ident could be a variable, parametername, or functioname.
        // Need to perform the correct semantic checks based on the type
        // of the identifier in the symbol table.

        switch (node.getKind())
        {
        case VAR:
            this.semanticAction37(node.getIdent(), node, true);
            this.semanticAction26(node);
            break;
        case PARAM:
            this.semanticAction39(node.getIdent(), node, true);
            this.semanticAction25(node, 25);
            break;
        case FUNC:
            this.semanticAction40(node.getIdent(), node, true);
            this.semanticAction42(node);
            this.semanticAction28(node);
            break;
        case UNKNOWN:
            if (this.semanticAction37(node.getIdent(), node, false))
            {
                // This is a variable.
                this.semanticAction26(node);
            } else if (this.semanticAction39(node.getIdent(), node, false))
            {
                // This is a parametername.
                this.semanticAction25(node, 25);
            } else if (this.semanticAction40(node.getIdent(), node, false))
            {
                // This is a functionname.
                this.semanticAction42(node);
                this.semanticAction28(node);
            } else {
                // This ident doesn't match anything expected in the symbol table
                // so report all the semantic errors.
                this.semanticErrors.add(new SemanticErrorException(37, node));
                this.semanticErrors.add(new SemanticErrorException(39, node));
                this.semanticErrors.add(new SemanticErrorException(40, node));
            }
        }
    }

    public void visit(SubsExpn node)
    {
        this.dispatch(node.getOperand());
        this.semanticAction31(node.getOperand());

        this.semanticAction38(node.getVariable(), node, true);
        this.semanticAction27(node);
    }

    public void visit(IntConstExpn node)
    {
        this.semanticAction21(node);
    }

    public void visit(UnaryMinusExpn node)
    {
        this.dispatch(node.getOperand());
        this.semanticAction31(node.getOperand());
        this.semanticAction21(node);
    }

    public void visit(ArithExpn node)
    {
        this.dispatch(node.getLeft());
        this.semanticAction31(node.getLeft());

        this.dispatch(node.getRight());
        this.semanticAction31(node.getRight());

        this.semanticAction21(node);
    }

    public void visit(BoolConstExpn node)
    {
        this.semanticAction20(node);
    }

    public void visit(NotExpn node)
    {
        this.dispatch(node.getOperand());
        this.semanticAction30(node.getOperand());
        this.semanticAction20(node);
    }

    public void visit(BoolExpn node)
    {
        this.dispatch(node.getLeft());
        this.semanticAction30(node.getLeft());

        this.dispatch(node.getRight());
        this.semanticAction30(node.getRight());

        this.semanticAction20(node);
    }

    public void visit(EqualsExpn node)
    {
        this.dispatch(node.getLeft());
        this.dispatch(node.getRight());
        this.semanticAction32(node);
        this.semanticAction20(node);
    }

    public void visit(CompareExpn node)
    {
        this.dispatch(node.getLeft());
        this.semanticAction31(node.getLeft());

        this.dispatch(node.getRight());
        this.semanticAction31(node.getRight());

        this.semanticAction20(node);
    }

    public void visit(ConditionalExpn node)
    {
        this.dispatch(node.getCondition());
        this.semanticAction30(node.getCondition());

        this.dispatch(node.getTrueValue());
        this.dispatch(node.getFalseValue());
        this.semanticAction33(node);
        this.semanticAction24(node);
    }

    public void visit(FunctionCallExpn node)
    {
        // If the ident isn't a function it does not make sense to check anything after this.
        if (!this.semanticAction40(node.getIdent(), node, true))return;

        // Only a function call with arguments will be FunctionCallExpn due to
        // the way jcup applies the grammar rules in AST construction. So we can
        // assume getArguments is not null.

        // It doesn't make sense to perform the other semantic checks if the number
        // of arguments doesn't match the number of formal parameters.
        if (!this.semanticAction43(node)) return;

        SymbolTableEntry entry = this.symTablesStack.peek().getEntry(node.getIdent());
        this.checkArguments(node.getArguments(), entry);

        this.semanticAction28(node);
    }

    /*
     *  ========================================================
     *  Implement each semantic action below.
     *  ========================================================
     */

    // Start program scope.
    private void semanticAction00(Program node)
    {
        this.traceSemanticAction(0);
        this.globalSymTable = this.symTablesStack.push(new SymbolTable());
        node.setSymTable(this.globalSymTable);
    }

    // End program scope.
    private void semanticAction01()
    {
        this.traceSemanticAction(1);
        this.symTablesStack = null;
    }

    // Associate declaration(s) with scope.
    private void semanticAction02()
    {
        this.traceSemanticAction(2);

        // This may be a no-op since declarations are added to the symbol when they
        // are declared.
    }

    // Start function scope.
    private void semanticAction04(RoutineDecl node, int actualActionNum)
    {
        this.traceSemanticAction(actualActionNum);

        // Don't want to link this new table as a child of the current
        // scope yet because the function/routine could be a duplicate identifier.
        // Link the table into the parent scope when the function is successfully declared.
        this.pushTable(node.getRoutineBody().getBody(),
                       new SymbolTable(), false, false);
    }

    // End function scope.
    private void semanticAction05(int actualActionNum)
    {
        this.traceSemanticAction(actualActionNum);
        this.symTablesStack.pop();
    }

    // Start ordinary scope.
    private void semanticAction06(Scope node)
    {
        this.traceSemanticAction(6);

        this.pushTable(node, new SymbolTable(), true, true);
    }

    // End ordinary scope.
    private void semanticAction07()
    {
        this.semanticAction05(7);
    }

    // Start procedure scope.
    private void semanticAction08(RoutineDecl node)
    {
        this.semanticAction04(node, 8);
    }

    // End procedure scope.
    private void semanticAction09()
    {
        this.semanticAction05(9);
    }

    // Declare scalar variable.
    private void semanticAction10(ScalarDeclPart node)
    {
        this.traceSemanticAction(10);

        node.setEntry(new SymbolTableEntry(node));
        this.addEntryToCurrentTable(node.getEntry(), node, 10);
    }

    // Declare function with no parameters and specified type.
    private void semanticAction11(RoutineDecl node, int actionNum)
    {
        this.traceSemanticAction(actionNum);

        // This semantic action will handle declaring all procedures and functions.
        // Other semantic actions will just call this function.

        SymbolTableEntry entry = new SymbolTableEntry(node);
        node.setEntry(entry);
        int count = 0;

        if (node.getRoutineBody().getParameters() != null)
        {
            LinkedList<ScalarDecl> params = node.getRoutineBody().getParameters().getNodes();
            for (ScalarDecl s : params)
            {
                entry.getRelatedSymbols().add(s.getEntry());
                ++count;
            }

            CallableSymbolType t = (CallableSymbolType)entry.getType();
            t.setNumParameters(count);
        }

        // Top of symbol table stack at this point should be the scope of the routine.
        SymbolTable rtable = this.symTablesStack.pop();

        // Top of the symbol table stack is now the symbol table where the routine's symbol
        // should be added.
        if (this.addEntryToCurrentTable(entry, node, actionNum))
        {
            // Now it's safe to link this routine's scope into the parent.
            this.symTablesStack.peek().addChildScope(node.getRoutineBody().getBody(),
                                                     rtable, false);
        }

        // Put the rtable back on the stack as it will be popped off in another semantic action.
        this.symTablesStack.push(rtable);
    }

    // Declare function with parameters and specified type.
    private void semanticAction12(RoutineDecl node)
    {
        this.semanticAction11(node, 12);
    }

    // Associate scope with function/procedure.
    private void semanticAction13()
    {
        this.traceSemanticAction(13);
        // This is a no-op
    }

    // Set parameter count to 0.
    private void semanticAction14()
    {
        this.traceSemanticAction(14);
        // This is a no-op.
    }

    // Declare parameter with specified type.
    private void semanticAction15(ScalarDecl node)
    {
        this.traceSemanticAction(15);

        node.setEntry(new SymbolTableEntry(node));
        this.addEntryToCurrentTable(node.getEntry(), node, 15);
    }

    // Increment parameter count by 1.
    private void semanticAction16()
    {
        this.traceSemanticAction(16);
        // This is a no-op.
    }

    // Declare procedure with no parameters.
    private void semanticAction17(RoutineDecl node)
    {
        this.semanticAction11(node, 17);
    }

    // Declare procedure with parameters.
    private void semanticAction18(RoutineDecl node)
    {
        this.semanticAction11(node, 18);
    }

    // Declare array variable with specified lower and upper bounds.
    private void semanticAction19(ArrayDeclPart node, int actionNum)
    {
        this.traceSemanticAction(actionNum);

        node.setEntry(new SymbolTableEntry(node));
        this.addEntryToCurrentTable(node.getEntry(), node, 19);
    }

    // Set result type to boolean
    private void semanticAction20(Expn node)
    {
        this.traceSemanticAction(20);
        node.setEvalType(SymbolTableEntry.BOOLEAN_SYMBOL_TYPE);
    }

    // Set result type to integer
    private void semanticAction21(Expn node)
    {
        this.traceSemanticAction(21);
        node.setEvalType(SymbolTableEntry.INTEGER_SYMBOL_TYPE);
    }

    // Set result type to type of expression
    private void semanticAction23(Expn node)
    {
        this.traceSemanticAction(23);
        // Nothing to do for this action
    }

    // Set result type to type of conditional expressions
    private void semanticAction24(ConditionalExpn node)
    {
        this.traceSemanticAction(24);
        node.setEvalType(node.getTrueValue().getEvalType());
    }

    // Set result type to type of parametername
    private void semanticAction25(IdentExpn node, int actionNum)
    {
        this.traceSemanticAction(actionNum);

        SymbolTableEntry te = this.symTablesStack.peek().getEntry(node.getIdent());
        if (te != null)
        {
            node.setEvalType(te.getType());
        }
    }

    // Set result type to type of variablename
    private void semanticAction26(IdentExpn node)
    {
        this.semanticAction25(node, 26);
    }

    // Set result type to type of array element
    private void semanticAction27(SubsExpn node)
    {
        this.traceSemanticAction(27);

        SymbolTableEntry te = this.symTablesStack.peek().getEntry(node.getVariable());
        if (te != null && te.getType() instanceof ArraySymbolType)
        {
            node.setEvalType(((ArraySymbolType)te.getType()).getElementType());
        }
    }

    // Set result type to result type of function
    private void semanticAction28(Expn node)
    {
        this.traceSemanticAction(28);

        // node can be a IdentExpn or FunctionCallExpn.
        SymbolTableEntry te;

        if (node instanceof IdentExpn)
        {
            te = this.symTablesStack.peek().getEntry(((IdentExpn)node).getIdent());
        } else {
            te = this.symTablesStack.peek().getEntry(((FunctionCallExpn)node).getIdent());
        }

        node.setEvalType(((CallableSymbolType)te.getType()).getReturnType());
    }

    // Check that type of expression is boolean
    private boolean semanticAction30(Expn node)
    {
        this.traceSemanticAction(30);

        if (node.getEvalType() instanceof BooleanSymbolType) return true;

        this.semanticErrors.add(new SemanticErrorException(30, node));
        return false;
    }

    // Check that type of expression or variable is integer
    private boolean semanticAction31(Expn node)
    {
        this.traceSemanticAction(31);

        if (node.getEvalType() instanceof IntegerSymbolType) return true;

        this.semanticErrors.add(new SemanticErrorException(31, node));
        return false;
    }

    // Check that left and right operand expressions are the same type
    private boolean semanticAction32(BinaryExpn node)
    {
        this.traceSemanticAction(32);

        if (node.getLeft().getEvalType().equals(node.getRight().getEvalType())) return true;

        this.semanticErrors.add(new SemanticErrorException(32, node));
        return false;
    }

    // Check that both result expressions in conditional are the same type
    private boolean semanticAction33(ConditionalExpn node)
    {
        this.traceSemanticAction(33);

        if (node.getTrueValue().getEvalType().equals(node.getFalseValue().getEvalType())) return true;

        this.semanticErrors.add(new SemanticErrorException(33, node));
        return false;
    }

    // Check that variable and expression in assignment are the same type
    private boolean semanticAction34(Expn node1, Expn node2)
    {
        this.traceSemanticAction(34);

        if (node1.getEvalType() != null &&
            node1.getEvalType().equals(node2.getEvalType())) return true;

        this.semanticErrors.add(new SemanticErrorException(34, node1));
        return false;
    }

    // Check that expression type matches the return type of enclosing function
    private boolean semanticAction35(Expn node)
    {
        this.traceSemanticAction(35);

        RoutineDecl r;
        try
        {
            r = this.routineStack.peek();
        } catch (EmptyStackException e)
        {
            // We're not in a function right now but S35 does not check that
            // so don't report an error.
            return false;
        }

        SymbolTableEntry func = this.symTablesStack.peek().getEntry(r.getName());
        // func is actually a routine but S35 should not report this error.
        if (func.getKind() != Kind.FUNC) return false;

        CallableSymbolType t = (CallableSymbolType)func.getType();
        if (t.getReturnType().equals(node.getEvalType())) return true;

        this.semanticErrors.add(new SemanticErrorException(35, node));
        return false;
    }

    // Check that type of argument expression matches type of corresponding formal parameter
    private boolean semanticAction36(Expn argnode, SymbolTableEntry param)
    {
        this.traceSemanticAction(36);

        if (argnode.getEvalType().equals(param.getType())) return true;

        this.semanticErrors.add(new SemanticErrorException(31, argnode));
        return false;
    }

    // Check that identifier has been declared as a scalar variable
    private boolean semanticAction37(String ident, Locatable node, boolean reportErrors)
    {
        this.traceSemanticAction(37);

        return this.symbolTypeCheck(ident, node, ScalarSymbolType.class, 37, reportErrors);
    }

    // Check that identifier has been declared as an array
    private boolean semanticAction38(String ident, Locatable node, boolean reportErrors)
    {
        this.traceSemanticAction(38);

        return this.symbolTypeCheck(ident, node, ArraySymbolType.class, 38, reportErrors);
    }

    // Check that identifier has been declared as a parameter
    private boolean semanticAction39(String ident, Locatable node, boolean reportErrors)
    {
        this.traceSemanticAction(39);

        return this.kindCheck(ident, node, Kind.PARAM, 39, reportErrors);
    }

    // Check that identifier has been declared as a function
    private boolean semanticAction40(String ident, Locatable node, boolean reportErrors)
    {
        this.traceSemanticAction(40);

        return this.kindCheck(ident, node, Kind.FUNC, 40, reportErrors);
    }

    // Check that identifier has been declared as a procedure.
    private boolean semanticAction41(String ident, Locatable node, boolean reportErrors)
    {
        this.traceSemanticAction(41);

        return this.kindCheck(ident, node, Kind.PROC, 41, reportErrors);
    }

    // Check that the function or procedure has no parameters
    private boolean semanticAction42(AST node)
    {
        this.traceSemanticAction(42);

        SymbolTable currentScope = this.symTablesStack.peek();
        String ident;
        if (node instanceof FunctionCallExpn)
        {
            ident = ((FunctionCallExpn)node).getIdent();
        } else if (node instanceof ProcedureCallStmt)
        {
            ident = ((ProcedureCallStmt)node).getName();
        } else {
            // Abort this check without adding a semantic error
            // since this check isn't responsible for reporting
            // this error.
            return false;
        }

        try
        {
            CallableSymbolType t = (CallableSymbolType)currentScope.getEntry(ident).getType();
            if (t.getNumParameters() == 0) return true;
        } catch (NullPointerException | ClassCastException e)
        {
            // This identifier is missing or some error occurred and it is not the
            // role of this semantic action to check it.
            return false;
        }

        this.semanticErrors.add(new SemanticErrorException(42, node));
        return false;
    }

    // Check that the number of arguments is equal to the number of formal parameters
    private boolean semanticAction43(AST node)
    {
        this.traceSemanticAction(43);

        SymbolTable currentScope = this.symTablesStack.peek();
        String ident;
        ASTList<Expn> args;
        if (node instanceof FunctionCallExpn)
        {
            FunctionCallExpn n = (FunctionCallExpn)node;
            ident = n.getIdent();
            args = n.getArguments();
        } else if (node instanceof ProcedureCallStmt)
        {
            ProcedureCallStmt p = (ProcedureCallStmt)node;
            ident = p.getName();
            args = p.getArguments();
        } else {
            // Abort this check.
            return false;
        }

        try
        {
            CallableSymbolType t = (CallableSymbolType)currentScope.getEntry(ident).getType();
            if (t.getNumParameters() == args.size()) return true;
        } catch (NullPointerException | ClassCastException npe)
        {
            // This identifier is missing or some error occurred and it is not the
            // role of this semantic action to check it.
            return false;
        }

        this.semanticErrors.add(new SemanticErrorException(43, node));
        return false;
    }

    // Set argument counter to 0.
    private void semanticAction44()
    {
        this.traceSemanticAction(44);
        // This is a no-op.
    }

    // Increment argument counter by one.
    private void semanticAction45()
    {
        this.traceSemanticAction(45);
        // This is a no-op.
    }

    // Check that lower bound is <= upper bound.
    private boolean semanticAction46(ArrayDeclPart node)
    {
        this.traceSemanticAction(46);

        if (node.getLowerBoundary() <= node.getUpperBoundary()) return true;

        this.semanticErrors.add(new SemanticErrorException(46, node));
        return false;
    }

    // Associate type with variables.
    private void semanticAction47(MultiDeclarations node)
    {
        this.traceSemanticAction(47);

        LinkedList<DeclarationPart> declParts = node.getElements().getNodes();
        ScalarSymbolType type = (ScalarSymbolType)SymbolTableEntry.convertType(node.getType());
        for (DeclarationPart dp : declParts)
        {
            SymbolTableEntry entry = this.symTablesStack.peek().getEntry(dp.getName());
            if (entry == null) continue;

            if (entry.getType() instanceof ArraySymbolType)
            {
                ((ArraySymbolType)entry.getType()).setElementType(type);
            } else
            {
                entry.setType(type);
            }
        }
    }

    // Declare array variable with specified upper bound.
    private void semanticAction48(ArrayDeclPart node)
    {
        this.semanticAction19(node, 48);
    }

    // Check that exit statement is inside a loop.
    private boolean semanticAction50(ExitStmt node)
    {
        this.traceSemanticAction(50);

        if (!this.loopStack.empty()) return true;

        this.semanticErrors.add(new SemanticErrorException(50, node));
        return false;
    }

    // Check that return is inside a function.
    private boolean semanticAction51(ReturnStmt node)
    {
        this.traceSemanticAction(51);

        try
        {
            String funcName = this.routineStack.peek().getName();
            return this.kindCheck(funcName, node, Kind.FUNC, 51, true);
        } catch (EmptyStackException e)
        {
            // We're not in a function at all right now.
            this.semanticErrors.add(new SemanticErrorException(51, node));
        }

        return false;
    }

    // Check that return is inside a procedure.
    private boolean semanticAction52(ReturnStmt node)
    {
        this.traceSemanticAction(52);

        try
        {
            String procName = this.routineStack.peek().getName();
            return this.kindCheck(procName, node, Kind.PROC, 52, true);
        } catch (EmptyStackException e)
        {
            // We're not in a procedure at all right now.
            this.semanticErrors.add(new SemanticErrorException(52, node));
        }

        return false;
    }

    // Check that integer is > 0 and <= number of containing loops.
    private boolean semanticAction53(ExitStmt node)
    {
        this.traceSemanticAction(53);

        int level = node.getLevel();
        if (level > 0 && level <= this.loopStack.size()) return true;

        this.semanticErrors.add(new SemanticErrorException(53, node));
        return false;
    }

    private SymbolTable pushTable(Scope node, SymbolTable table, boolean linkChild, boolean isMinor)
    {
        if (linkChild) this.symTablesStack.peek().addChildScope(node, table, isMinor);
        else table.setParentScope(this.symTablesStack.peek(), isMinor);

        node.setSymTable(table);
        this.symTablesStack.push(table);
        return table;
    }

    private boolean addEntryToCurrentTable(SymbolTableEntry entry, Locatable node, int actionNum)
    {
        if (!this.symTablesStack.peek().addEntry(entry))
        {
            this.semanticErrors.add(new SemanticErrorException(actionNum, node));
            return false;
        }

        return true;
    }

    private boolean symbolTypeCheck(String ident, Locatable node,
                                    Class<? extends SymbolType> type,
                                    int actionNum, boolean reportErrors)
    {
        SymbolTableEntry e = this.symTablesStack.peek().getEntry(ident);

        try {
            if (type.isInstance(e.getType())) return true;
        } catch (NullPointerException npe) { /* Nothing to do here. */ }

        if (reportErrors) this.semanticErrors.add(new SemanticErrorException(actionNum, node));
        return false;
    }

    private boolean kindCheck(String ident, Locatable node, Kind k,
                              int actionNum, boolean reportErrors)
    {
        SymbolTableEntry e = this.symTablesStack.peek().getEntry(ident);
        return this.kindCheck(e, node, k, actionNum, reportErrors);
    }

    private boolean kindCheck(SymbolTableEntry e, Locatable node, Kind k,
                              int actionNum, boolean reportErrors)
    {
        try {
            if (e.getKind() == k) return true;
        } catch (NullPointerException npe) { /* Nothing to do here. */ }

        if (reportErrors) this.semanticErrors.add(new SemanticErrorException(actionNum, node));
        return false;
    }

    private void checkArguments(ASTList<Expn> args, SymbolTableEntry entry)
    {
        this.semanticAction44();
        LinkedList<Expn> expns = args.getNodes();
        int count = 0;
        for (Expn e : expns)
        {
            this.dispatch(e);
            this.semanticAction45();
            this.semanticAction36(e, entry.getRelatedSymbols().get(count));

            ++count;
        }
    }

    /**
     * Trace a semantic action.
     * @param actionNumber semantic action number to trace
     */
    private void traceSemanticAction(int actionNumber)
    {
        if (this.traceSemantics)
        {
            if (this.traceFile.length() > 0)
            {
                //output trace to the file represented by traceFile
                try
                {
                    //open the file for writing and append to it
                    FileWriter tracer = new FileWriter(traceFile, true);

                    tracer.write("Semantics: S" + actionNumber + "\n");
                    //always be sure to close the file
                    tracer.close();
                } catch (IOException e)
                {
                    System.out.println(traceFile +
                                       " couldn't be opened/created. It may be in use.");
                }
            } else
            {
                //output the trace to standard out.
                System.out.println("Semantics: S" + actionNumber);
            }
        }
    }
}
