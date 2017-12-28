package compiler488.codegen;

import java.util.*;

import compiler488.ast.*;
import compiler488.ast.Readable;
import compiler488.ast.decl.*;
import compiler488.ast.expn.*;
import compiler488.ast.stmt.*;
import compiler488.runtime.Machine;
import compiler488.runtime.MemoryAddressException;
import compiler488.symbol.*;
import compiler488.symbol.type.*;
import compiler488.visitor.VisitorBase;

/**
 *<pre>
 *  Code Generation Conventions
 *
 *  To simplify the course project, this code generator is
 *  designed to compile directly to pseudo machine memory
 *  which is available as the private array memory[]
 *
 *  It is assumed that the code generator places instructions
 *  in memory in locations
 *
 *      memory[ 0 .. startMSP - 1 ]
 *
 *  The code generator may also place instructions and/or
 *  constants in high memory at locations (though this may
 *  not be necessary)
 *      memory[ startMLP .. Machine.memorySize - 1 ]
 *
 *  During program exection the memory area
 *      memory[ startMSP .. startMLP - 1 ]
 *  is used as a dynamic stack for storing activation records
 *  and temporaries used during expression evaluation.
 *  A hardware exception (stack overflow) occurs if the pointer
 *  for this stack reaches the memory limit register (mlp).
 *
 *  The code generator is responsible for setting the global
 *  variables:
 *      startPC         initial value for program counter
 *      startMSP        initial value for msp
 *      startMLP        initial value for mlp
 * </pre>
 * @author  <B>Dawing Cho</B>
 * @author  <B>Felicia Hoie</B>
 * @author  <B>Ishtiaque Khaled</B>
 * @author  <B>Sunny Li</B>
 * @author  <B>James Yuan</B>
 */

public class CodeGen extends VisitorBase
{
    /** version string for Main's -V */
    public static final String version = "Winter 2017" ;

    /**
     * Generic struct for returning information from each construct that code is generated for.
     */
    private class GenData {

        /** Address of first instruction in the construct. */
        short startAddr = -1;

        /**
         * List of list of memory locations that need to be patched with the
         * correct value.
         * Each list inside the outerlist represents a group of memory locations
         * that need to be patched with the same value.
         * This is implemented as a list of lists to enable more than two unique
         * values to be patched (unlike in the genData struct in the lecture notes).
         */
        ArrayList<ArrayList<Short>> patchAddresses = new ArrayList<>();
    }

    /**
     * Store info on loops currently being traversed in the AST.
     */
    private class LoopData
    {
        LoopingStmt loop;

        /** Symbol table associated with the scope of the loop */
        SymbolTable symbolTable;

        /**
         * Addresses that should be patched with the address of the first instruction
         * after the loop.
         */
        ArrayList<Short> patchAddresses = new ArrayList<>();

    }

    /** size of control data in words for each activation record */
    private static final short CONTROL_DATA_SIZE = 3;

    /** initial value for memory stack pointer */
    private short startMSP = 0;
    /** initial value for program counter */
    private short startPC = 0;
    /** initial value for memory limit pointer */
    private short startMLP = Machine.memorySize - 1;

    /**
     * Current active scope in the AST traversal.
     * Don't need a stack because parent-child relationships have
     * already been linked when the symbol tables were constructed
     * during semantic analysis.
     */
    private SymbolTable currScope = null;

    /**
     * Stack for saving GenData when recursively traversing the AST.
     * This is needed because the signatures of the dispatch and
     * visit functions cannot be changed.
     * The top element in the stack always represents the GenData
     * instance for the currently visited AST node.
     */
    private Stack<GenData> genDataStack = new Stack<>();

    /**
     * stack for keeping track of the loops currently being traversed
     * implemented as an ArrayList because access to elements by index
     * is required
     */
    private ArrayList<LoopData> loopInfoList = new ArrayList<>();

    /**
     * Hashmap for storing the memory addresses of strings.
     */
    private HashMap<String, Short> stringTable = new HashMap<>();

    /**
     *  Perform any requred cleanup at the end of code generation.
     *  Called once at the end of code generation.
     */
    private void Finalize()
    {
        Machine.setPC(this.startPC);     /* where code to be executed begins */
        Machine.setMSP(this.startMSP);   /* where memory stack begins */
        Machine.setMLP((this.startMLP)); /* limit of stack */
    }

    public void doTraversal(Program node)
    {
        this.visit(node);
        this.Finalize();
    }

    /*
     *  ========================================================
     *  Overload a visit method for each AST node type below.
     *  ========================================================
     */

    public void visit(Program node)
    {
        // The program will start by calling a routine at lexical level 0
        // which represents the statements in the program scope.
        GenData gdPr = this.emitRoutinePrologue((short)0, new ASTList<>());
        this.startPC = gdPr.startAddr;

        // Patch the return address of the "program routine".
        short epStartAddr = this.emitRoutineEpilogue((short)0, true);
        this.writeMemoryHelper(gdPr.patchAddresses.get(1).get(0), epStartAddr);

        // Terminate program execution after the routine epilogue for the "program routine"
        this.writeNextMemoryAddr(Machine.HALT);

        // Need to manually push a GenData instance since we are calling visit directly instead
        // of calling dispatchWithGenData. We don't want to dispatch in this case as it will
        // just result in calling this method again.
        this.genDataStack.push(new GenData());
        this.visit((Scope)node);

        // Patch the start address of the "program routine".
        GenData gdProgramScope = this.genDataStack.pop();
        this.writeMemoryHelper(gdPr.patchAddresses.get(0).get(0), gdProgramScope.startAddr);
    }

    public void visit(AssignStmt node)
    {
        // Emit code to evaluate memory address of LHS
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getLval()).startAddr;
        // LHS should be an IdentExpn representing a variable. By default a variable IdentExpn
        // loads the variable's value to the top of the stack. However, when an IdentExpn is on
        // the LHS of a statement we just want the address of the variable at the top of the stack.
        // The next line delete the LOAD instruction that we don't want.
        --this.startMSP;

        // Emit code to evaluate RHS expression.
        this.dispatchWithGenData(node.getRval());
        this.writeNextMemoryAddr(Machine.STORE);
    }

    public void visit(IfStmt node)
    {
        // Emit code to evaluate condition.
        GenData gdExpn = this.dispatchWithGenData(node.getCondition());
        this.genDataStack.peek().startAddr = gdExpn.startAddr;

        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with the address of the first instruction in the else clause
        // or the first instruction after the if statement if there is no else
        // clause.
        short falseAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.BF);

        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with the address of the first instruction in the if clause
        short trueAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.BR);

        GenData gdTrue = this.dispatchWithGenData(node.getWhenTrue());
        this.writeMemoryHelper(trueAddr, gdTrue.startAddr);

        if (node.getWhenFalse() != null)
        {
            this.writeNextMemoryAddr(Machine.PUSH);
            // Patch with address of first instruction after this construct.
            short finishAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
            this.writeNextMemoryAddr(Machine.BR);

            GenData gdFalse = this.dispatchWithGenData(node.getWhenFalse());
            this.writeMemoryHelper(falseAddr, gdFalse.startAddr);

            this.writeMemoryHelper(finishAddr, this.startMSP);
        } else
        {
            // Patch with address of first instruction after if-statement
            this.writeMemoryHelper(falseAddr, this.startMSP);
        }
    }

    public void visit(RepeatUntilStmt node)
    {
        this.enterLoopHelper(node);

        // Emit code for loop body.
        GenData gdBody = this.dispatchWithGenData(node.getBody());
        this.genDataStack.peek().startAddr = gdBody.startAddr;

        // Emit code to evaluate condition.
        this.dispatchWithGenData(node.getExpn());

        // Emit code to branch to top of the loop body
        this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(gdBody.startAddr);
        this.writeNextMemoryAddr(Machine.BF);

        this.exitLoopHelper(node);
    }

    public void visit(WhileDoStmt node)
    {
        this.enterLoopHelper(node);

        // Emit code to evaluate condition.
        GenData gdExpn = this.dispatchWithGenData(node.getExpn());
        this.genDataStack.peek().startAddr = gdExpn.startAddr;

        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with the address of the first instruction after the loop.
        short falseAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.BF);

        // Emit code for loop body.
        this.dispatchWithGenData(node.getBody());

        this.writeNextMemoryAddr(Machine.PUSH);
        // Emit code to branch to the top of the loop condition.
        this.writeNextMemoryAddr(gdExpn.startAddr);
        this.writeNextMemoryAddr(Machine.BR);

        // Patch with address of instruction after loop.
        this.writeMemoryHelper(falseAddr, this.startMSP);

        this.exitLoopHelper(node);
    }

    public void visit(ExitStmt node)
    {
        int exitLevel = node.hasExitWithLevel() ? node.getLevel() : 1;
        LoopData ld = this.loopInfoList.get(this.loopInfoList.size() - exitLevel);

        short falseAddr = -1;
        if (node.getExpn() != null)
        {
            this.setStartAddrIfEmpty(this.dispatchWithGenData(node.getExpn()).startAddr);
            this.writeNextMemoryAddr(Machine.PUSH);
            // Patch with instruction of first instruction after this exit statement.
            falseAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
            this.writeNextMemoryAddr(Machine.BF);
        }

        if (ld.symbolTable != null)
        {
            this.setStartAddrIfEmpty(this.emitDeallocateScope(ld.symbolTable.getScope(),
                                                              ld.symbolTable.getInitialOrderNum()));
        }

        this.setStartAddrIfEmpty(this.writeNextMemoryAddr(Machine.PUSH));
        // Patch with address of first instruction after the loop.
        ld.patchAddresses.add(this.writeNextMemoryAddr(Machine.UNDEFINED));
        this.writeNextMemoryAddr(Machine.BR);

        if (falseAddr != -1) this.writeMemoryHelper(falseAddr, this.startMSP);
    }

    public void visit(ReturnStmt node)
    {
        if (node.getValue() != null)
        {
            // Evaluate the return value and store it in the designated spot in
            // the activation record.
            this.setStartAddrIfEmpty(this.writeNextMemoryAddr(Machine.ADDR));
            this.writeNextMemoryAddr(this.currScope.getScope());
            this.writeNextMemoryAddr((short)0);
            this.dispatchWithGenData(node.getValue());
            this.writeNextMemoryAddr(Machine.STORE);
        }

        this.setStartAddrIfEmpty(this.emitBranchToReturnAddress());
    }

    public void visit(WriteStmt node)
    {
        LinkedList<Printable> printables = node.getOutputs().getNodes();

        for (Printable p : printables)
        {
            this.setStartAddrIfEmpty(this.dispatchWithGenData(p).startAddr);

            if (p instanceof SkipConstExpn)
            {
                // Top of stack holds ASCII code for '\n'
                this.writeNextMemoryAddr(Machine.PRINTC);
            } else if (p instanceof TextConstExpn)
            {
                // Top of stack holds the memory address of the first character in the
                // text constant.
                // firstAddr is the instruction to branch to for loading the next character
                // in the string
                short firstAddr = this.writeNextMemoryAddr(Machine.DUP);
                this.writeNextMemoryAddr(Machine.LOAD);
                this.writeNextMemoryAddr(Machine.DUP);
                this.writeNextMemoryAddr(Machine.PUSH);

                // Check if the next character in the string literal is the null terminator
                this.writeNextMemoryAddr((short)0);
                this.writeNextMemoryAddr(Machine.EQ);
                // Need to negate the check since we only have a BF instruction.
                this.emitLogicalNegation();

                this.writeNextMemoryAddr(Machine.PUSH);
                // Patch with address of instruction after the loop for loading
                // characters in the string. Branch to that address
                short finishAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
                this.writeNextMemoryAddr(Machine.BF);

                // Print the character and repeat.
                this.writeNextMemoryAddr(Machine.PRINTC);
                this.writeNextMemoryAddr(Machine.PUSH);
                // Advance memory address to next char.
                this.writeNextMemoryAddr((short)1);
                this.writeNextMemoryAddr(Machine.ADD);
                this.writeNextMemoryAddr(Machine.PUSH);
                this.writeNextMemoryAddr(firstAddr);
                this.writeNextMemoryAddr(Machine.BR);

                // Remove null terminator and its memory address from the stack.
                this.writeMemoryHelper(finishAddr, this.writeNextMemoryAddr(Machine.POP));
                this.writeNextMemoryAddr(Machine.POP);
            } else
            {
                // Top of stack holds value of integer variable
                this.writeNextMemoryAddr(Machine.PRINTI);
            }
        }
    }

    public void visit(ReadStmt node)
    {
        LinkedList<Readable> readables = node.getInputs().getNodes();
        for (Readable r : readables)
        {
            this.setStartAddrIfEmpty(this.dispatchWithGenData(r).startAddr);

            // Delete the LOAD instruction since we want the memory address at the
            // top of the stack instead of the value in this case.
            if (r instanceof IdentExpn)this.startMSP--;

            this.writeNextMemoryAddr(Machine.READI);
            this.writeNextMemoryAddr(Machine.STORE);
        }
    }

    public void visit(ProcedureCallStmt node)
    {
        SymbolTableEntry ste = this.currScope.getEntry(node.getName());

        ASTList<Expn> args = node.getArguments();
        if (args == null) args = new ASTList<>();

        this.emitRoutineCall(ste, args);
    }

    public void visit(MultiDeclarations node)
    {
        short startAddr = -1;
        LinkedList<DeclarationPart> dps = node.getElements().getNodes();
        for (DeclarationPart dp : dps)
        {
            GenData gd = this.dispatchWithGenData(dp);
            if (startAddr == (short)-1) this.genDataStack.peek().startAddr = gd.startAddr;
        }
    }

    public void visit(RoutineDecl node)
    {
        // Since nested routine declarations are allowed in the language, we need to branch
        // past them because the instructions will be emitted inside the instructions
        // of the outer routine.

        // This memory location need to be patched with the address of the instruction
        // right after the routine declaration.
        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.PUSH);
        short branchAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.BR);

        // The start address of a RoutineDecl node is branchAddr but the first
        // instruction of the routine itself is the first instruction in the scope.
        // For recursive function calls we need the start address of the routine before
        // dispatchWithGenData returns. We can safely assume the next instruction is the first
        // instruction of the routine.
        SymbolTableEntry ste = this.currScope.getEntry(node.getName());
        ste.getLaddr().setOrderNumber(this.startMSP);
        this.dispatchWithGenData(node.getRoutineBody().getBody());

        // Patch branchAddr with address of instruction after procedure definition.
        this.writeMemoryHelper(branchAddr, this.startMSP);
    }

    public void visit(ScalarDeclPart node)
    {
        // Allocate space for a scalar variable on the activation record.
        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(Machine.UNDEFINED);
    }

    public void visit(ArrayDeclPart node)
    {
        // Calculate the size of the array in words.
        SymbolTableEntry ste = this.currScope.getEntry(node.getName());
        ArraySymbolType ast = (ArraySymbolType)ste.getType();
        int size = ast.getUpperBound() - ast.getLowerBound() + 1;

        // Allocate space for an array on the activation record.
        // The size of an array can exceed the maximum value of a short so might
        // need to split the allocation into multiple steps.
        do
        {
            short tempSize = size > Short.MAX_VALUE ? Short.MAX_VALUE : (short)size;
            if (size > Short.MAX_VALUE) size -= Short.MAX_VALUE;

            this.setStartAddrIfEmpty(this.writeNextMemoryAddr(Machine.PUSH));
            this.writeNextMemoryAddr(Machine.UNDEFINED);
            this.writeNextMemoryAddr(Machine.PUSH);
            this.writeNextMemoryAddr(tempSize);
            this.writeNextMemoryAddr(Machine.DUPN);
        } while (size > Short.MAX_VALUE);
    }

    public void visit(Scope node)
    {
        if (node instanceof Program) this.currScope = this.globalSymTable;
        else this.currScope = this.currScope.getChildScope(node);

        if (this.currScope == null) throw new CodeGenErrorException("Current scope is null");

        // Start address of a Scope node is the address of the first instruction in the
        // first declaration or first statement if there are no declarations.

        LinkedList<Declaration> declarations = node.getDeclarations().getNodes();
        for (Declaration d : declarations)
        {
            this.setStartAddrIfEmpty(this.dispatchWithGenData(d).startAddr);
        }

        LinkedList<Stmt> statements = node.getStatements().getNodes();
        for (Stmt s : statements)
        {
            this.setStartAddrIfEmpty(this.dispatchWithGenData(s).startAddr);
        }

        if (this.currScope.getParentScope() != null &&
            this.currScope.getScope() == this.currScope.getParentScope().getScope())
        {
            // Deallocate variables declared in the minor scope.
            // For major scopes the deallocation occurs in the routine epilogue since
            // this code is unreachable due to the return statement.
            short deallocStartAddr = this.emitDeallocateScope(this.currScope.getScope(),
                                                              this.currScope.getInitialOrderNum());
            this.setStartAddrIfEmpty(deallocStartAddr);
        }

        if (node instanceof Program)
        {
            // Add implicit return statement to "program routine" so it can be properly terminated.
            this.setStartAddrIfEmpty(this.emitBranchToReturnAddress());
        }

        this.currScope = this.currScope.getParentScope();
    }

    public void visit(FunctionCallExpn node)
    {
        // Only a function call with arguments will be FunctionCallExpn due to
        // the way jcup applies the grammar rules in AST construction. So we can
        // assume getArguments is not null.
        SymbolTableEntry ste = this.currScope.getEntry(node.getIdent());

        this.emitRoutineCall(ste, node.getArguments());
    }

    public void visit(IdentExpn node)
    {
        SymbolTableEntry ste = this.currScope.getEntry(node.getIdent());

        // An IdentExpn could represent a scalar variable or a function call with no arguments.
        switch (ste.getKind())
        {
        case PARAM:
        case VAR:
            this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.ADDR);
            this.writeNextMemoryAddr(ste.getLaddr().getLexicLevel());
            this.writeNextMemoryAddr((short)(ste.getLaddr().getOrderNumber() +
                                             CodeGen.CONTROL_DATA_SIZE));
            this.writeNextMemoryAddr(Machine.LOAD);
            break;
        case FUNC:
            this.emitRoutineCall(ste, new ASTList<>());
            break;
        default:
            throw new CodeGenErrorException("Cannot emit code for IdentExpn that is not a " +
                                            "variable or function call");
        }
    }

    public void visit(SubsExpn node)
    {
        SymbolTableEntry ste = this.currScope.getEntry(node.getVariable());
        ArraySymbolType ast = (ArraySymbolType)ste.getType();

        // Get the base address of the array (address of first element).
        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.ADDR);
        this.writeNextMemoryAddr(ste.getLaddr().getLexicLevel());
        this.writeNextMemoryAddr((short)(ste.getLaddr().getOrderNumber() +
                                         CodeGen.CONTROL_DATA_SIZE));

        // Evaluate the subscript expression.
        this.dispatchWithGenData(node.getOperand());

        // Calculate the offset from the start of the array.
        this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(ast.getLowerBound());
        this.writeNextMemoryAddr(Machine.SUB);

        // Load the element at the specified subscript.
        this.writeNextMemoryAddr(Machine.ADD);
        this.writeNextMemoryAddr(Machine.LOAD);
    }

    public void visit(IntConstExpn node)
    {
        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(node.getValue().shortValue());
    }

    public void visit(UnaryMinusExpn node)
    {
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getOperand()).startAddr;
        this.writeNextMemoryAddr(Machine.NEG);
    }

    public void visit(ArithExpn node)
    {
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getLeft()).startAddr;
        this.dispatchWithGenData(node.getRight());

        switch (node.getOpSymbol())
        {
        case "+":
            this.writeNextMemoryAddr(Machine.ADD);
            break;
        case "-":
            this.writeNextMemoryAddr(Machine.SUB);
            break;
        case "*":
            this.writeNextMemoryAddr(Machine.MUL);
            break;
        case "/":
            this.writeNextMemoryAddr(Machine.DIV);
            break;
        default:
            throw new CodeGenErrorException("Unknown opSymbol found in ArithExpn: " +
                                            node.getOpSymbol());
        }
    }

    public void visit(BoolConstExpn node)
    {
        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.PUSH);

        if (node.getValue()) this.writeNextMemoryAddr(Machine.MACHINE_TRUE);
        else this.writeNextMemoryAddr(Machine.MACHINE_FALSE);
    }

    public void visit(NotExpn node)
    {
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getOperand()).startAddr;
        this.emitLogicalNegation();
    }

    public void visit(BoolExpn node)
    {
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getLeft()).startAddr;

        switch (node.getOpSymbol())
        {
        case "or":
            this.dispatchWithGenData(node.getRight());
            this.writeNextMemoryAddr(Machine.OR);
            break;
        case "and":
            // Evaluate left side expression, and branch if false. 'and' expressions will
            // short-circuit.

            this.writeNextMemoryAddr(Machine.PUSH);
            // Patch with the address of evaluating the left side expression as MACHINE_FALSE.
            short falseLeft = this.writeNextMemoryAddr(Machine.UNDEFINED);
            this.writeNextMemoryAddr(Machine.BF);

            // Evaluate right side of expression
            this.dispatchWithGenData(node.getRight());

            this.writeNextMemoryAddr(Machine.PUSH);
            // Patch with the address of evaluating the right side expression as MACHINE_FALSE
            short falseRight = this.writeNextMemoryAddr(Machine.UNDEFINED);
            this.writeNextMemoryAddr(Machine.BF);

            // The result of both sides not branching, i.e., evaluating to MACHINE_TRUE
            this.writeNextMemoryAddr(Machine.PUSH);
            this.writeNextMemoryAddr(Machine.MACHINE_TRUE);

            this.writeNextMemoryAddr(Machine.PUSH);
            // Patch with the address of instruction after the false body
            short addrAfterFalseBody = this.writeNextMemoryAddr(Machine.UNDEFINED);
            this.writeNextMemoryAddr(Machine.BR);

            // The address of evaluating the expression as MACHINE_FALSE
            short falseResultAddr = this.writeNextMemoryAddr(Machine.PUSH);
            this.writeNextMemoryAddr(Machine.MACHINE_FALSE);

            // Patch addresses as above
            this.writeMemoryHelper(falseLeft, falseResultAddr);
            this.writeMemoryHelper(falseRight, falseResultAddr);
            this.writeMemoryHelper(addrAfterFalseBody, this.startMSP);

            break;
        default:
            throw new CodeGenErrorException("Unknown opSymbol found in BoolExpn: " +
                                            node.getOpSymbol());
        }
    }

    public void visit(EqualsExpn node)
    {
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getLeft()).startAddr;
        this.dispatchWithGenData(node.getRight());

        switch (node.getOpSymbol())
        {
        case "=":
            this.writeNextMemoryAddr(Machine.EQ);
            break;
        case "not =":
            this.writeNextMemoryAddr(Machine.EQ);
            this.emitLogicalNegation();
            break;
        default:
            throw new CodeGenErrorException("Unknown opSymbol found in EqualsExpn: " +
                                            node.getOpSymbol());
        }
    }

    public void visit(CompareExpn node)
    {
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getLeft()).startAddr;
        this.dispatchWithGenData(node.getRight());

        switch (node.getOpSymbol())
        {
        case "<":
            this.writeNextMemoryAddr(Machine.LT);
            break;
        case "<=":
            this.writeNextMemoryAddr(Machine.SWAP);
            this.writeNextMemoryAddr(Machine.LT);
            this.emitLogicalNegation();
            break;
        case ">=":
            this.writeNextMemoryAddr(Machine.LT);
            this.emitLogicalNegation();
            break;
        case ">":
            this.writeNextMemoryAddr(Machine.SWAP);
            this.writeNextMemoryAddr(Machine.LT);
            break;
        default:
            throw new CodeGenErrorException("Unknown opSymbol found in CompareExpn: " +
                                            node.getOpSymbol());
        }
    }

    public void visit(ConditionalExpn node)
    {
        // (a ? b : c)

        // Evaluate a.
        this.genDataStack.peek().startAddr = this.dispatchWithGenData(node.getCondition()).startAddr;

        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with the address of the first instruction in the false body
        short falseAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.BF);

        // Evaluate b.
        this.dispatchWithGenData(node.getTrueValue());

        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with address of first instruction after this construct.
        short trueAddr = this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.BR);

        // Evaluate c
        GenData gdFalse = this.dispatchWithGenData(node.getFalseValue());

        // Patch addresses as above
        this.writeMemoryHelper(falseAddr, gdFalse.startAddr);
        this.writeMemoryHelper(trueAddr, this.startMSP);
    }

    public void visit(TextConstExpn node)
    {
        Short addr = this.stringTable.get(node.getValue());

        if (addr == null)
        {
            // Put the string in the constant section of memory if this is a new string.

            // Add implicit null terminator.
            this.writeMemoryHelper(this.startMLP--, (short)'\0');

            // Write the string in reverse order while decrementing startMLP
            for (int i = node.getValue().length() - 1; i >= 0; --i)
            {
                this.writeMemoryHelper(this.startMLP--, (short)node.getValue().charAt(i));
            }

            // Save the starting address of the string.
            this.stringTable.put(node.getValue(), (short)(this.startMLP + 1));
            addr = this.stringTable.get(node.getValue());
        }

        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(addr);
    }

    public void visit(SkipConstExpn node)
    {
        // Emit the ASCII code for '\n'
        this.genDataStack.peek().startAddr = this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr((short)'\n');
    }

    /* ========================================================
     * Helper Functions.
     * ========================================================
     */

    /**
     * Wrapper function for calling {@code dispatch} and returning GenData for each call.
     * This is required because the signature for {@code dispatch} cannot be changed.
     * @param node the node to visit
     * @return the GenData instance populated from visiting {@code node}
     */
    private GenData dispatchWithGenData(Object node)
    {
        this.genDataStack.push(new GenData());
        this.dispatch(node);

        if (this.genDataStack.peek().startAddr == (short)-1)
        {
            throw new CodeGenErrorException("startAddr not set");
        }

        return this.genDataStack.pop();
    }

    /**
     * Emit code to deallocate variables in the topmost activation record.
     * @param ll the lexical level of the top most activation record
     * @param offsetFromControlData the lowest offset from the end of the control data
     *                              to keep allocated
     * @return the address of the first instruction emitted by this method
     */
    private short emitDeallocateScope(short ll, short offsetFromControlData)
    {
        short startAddr = this.writeNextMemoryAddr(Machine.PUSHMT);
        this.writeNextMemoryAddr(Machine.ADDR);
        this.writeNextMemoryAddr(ll);
        this.writeNextMemoryAddr((short)0);
        this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr((short)(offsetFromControlData + CodeGen.CONTROL_DATA_SIZE));
        this.writeNextMemoryAddr(Machine.ADD);
        this.writeNextMemoryAddr(Machine.SUB);
        this.writeNextMemoryAddr(Machine.POPN);

        return startAddr;
    }

    /**
     * Emit code to logically negate the value at the top of the runtime stack.
     * @return the address of the first instruction in this construct
     */
    private short emitLogicalNegation()
    {
        short startAddr = this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(Machine.MACHINE_FALSE);
        this.writeNextMemoryAddr(Machine.EQ);

        return startAddr;
    }

    /**
     * Emit code to branch to the return address stored in the activation record
     * at the top of the stack.
     * @return the address of the first instruction in this construct
     */
    private short emitBranchToReturnAddress()
    {
        // Load the return address from the activation record and branch to it.
        short startAddr = this.writeNextMemoryAddr(Machine.ADDR);
        this.writeNextMemoryAddr(this.currScope.getScope());
        // Return data is at the highest offset in the control data.
        this.writeNextMemoryAddr((short)(CodeGen.CONTROL_DATA_SIZE - 1));
        this.writeNextMemoryAddr(Machine.LOAD);
        this.writeNextMemoryAddr(Machine.BR);

        return startAddr;
    }

    /**
     * Emit the routine prologue which is used to setup the activation record
     * and display for function and procedure calls.
     * @param ll the lexical level of the called procedure
     * @param arguments the argument nodes for the routine call (must not be null)
     * @return the GenData instance containing the start address of the prologue
     *         and two lists of memory locations that need to be patched.
     *         The first list should be patched with the address of the first routine
     *         instruction in the routine and the second list should be patched with
     *         the return address.
     */
    private GenData emitRoutinePrologue(short ll, ASTList<Expn> arguments)
    {
        GenData gd = new GenData();
        // This list will store memory locations that need to be patched
        // with the address of the first instruction in the called routine.
        gd.patchAddresses.add(new ArrayList<>());
        // This list will store memory locations that need to be patched
        // with the return address.
        gd.patchAddresses.add(new ArrayList<>());

        gd.startAddr = this.writeNextMemoryAddr(Machine.PUSHMT);
        this.writeNextMemoryAddr(Machine.PUSH);
        this.writeNextMemoryAddr(Machine.UNDEFINED);
        this.writeNextMemoryAddr(Machine.SWAP);
        this.writeNextMemoryAddr(Machine.ADDR);
        this.writeNextMemoryAddr(ll);
        this.writeNextMemoryAddr((short)0);
        this.writeNextMemoryAddr(Machine.SWAP);
        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with return address.
        gd.patchAddresses.get(1).add(this.writeNextMemoryAddr(Machine.UNDEFINED));
        this.writeNextMemoryAddr(Machine.SWAP);

        // Emit code for evaluating the routine arguments.
        for (Expn e : arguments.getNodes())
        {
            this.dispatchWithGenData(e);
            // Keep the base address of the new activation record at the top of the stack.
            this.writeNextMemoryAddr(Machine.SWAP);
        }

        // Have to wait until after the arguments are evaluated before we can update the display.
        this.writeNextMemoryAddr(Machine.SETD);
        this.writeNextMemoryAddr(ll);

        this.writeNextMemoryAddr(Machine.PUSH);
        // Patch with address of first instruction in called routine.
        gd.patchAddresses.get(0).add(this.writeNextMemoryAddr(Machine.UNDEFINED));
        this.writeNextMemoryAddr(Machine.BR);

        return gd;
    }

    /**
     * Emit the routine epilogue which is used to teardown and update the display
     * when a routine returns.
     * @param ll the lexical level of the routine that has just returned
     * @param isProcedure a boolean flag indicating if the routine is a procedure
     * @return the start address of the routine epilogue
     */
    private short emitRoutineEpilogue(short ll, boolean isProcedure)
    {
        short startAddr = this.emitDeallocateScope(ll, (short)0);
        // Pop the return address as it's not needed anymore.
        this.writeNextMemoryAddr(Machine.POP);
        this.writeNextMemoryAddr(Machine.SETD);
        this.writeNextMemoryAddr(ll);

        // Pop the unused word for the return value if this is the epilogue
        // for a procedure call.
        if (isProcedure) this.writeNextMemoryAddr(Machine.POP);

        return startAddr;
    }

    /**
     * Emit code for calling a routine. This method handles setting the startAddr in the
     * GenData at the top of the stack.
     * @param ste the SymbolTableEntry representing the called routine
     * @param args the argument expns for the called routine
     */
    private void emitRoutineCall(SymbolTableEntry ste, ASTList<Expn> args)
    {
        boolean isProcedure = ((CallableSymbolType)ste.getType()).getReturnType() == null;

        GenData gdPr = this.emitRoutinePrologue(ste.getLaddr().getLexicLevel(), args);
        this.genDataStack.peek().startAddr = gdPr.startAddr;
        short epStartAddr = this.emitRoutineEpilogue(ste.getLaddr().getLexicLevel(), isProcedure);
        this.writeMemoryHelper(gdPr.patchAddresses.get(0).get(0), ste.getLaddr().getOrderNumber());
        this.writeMemoryHelper(gdPr.patchAddresses.get(1).get(0), epStartAddr);
    }

    /**
     * Perform some common setup when traversing a LoopStmt node.
     * @param node the LoopingStmt node
     */
    private void enterLoopHelper(LoopingStmt node)
    {
        LoopData ld = new LoopData();
        ld.loop = node;
        if (node.getBody() instanceof Scope)
        {
            ld.symbolTable = this.currScope.getChildScope((Scope)node.getBody());
        }
        this.loopInfoList.add(ld);
    }

    /**
     * Perform some common teardown when traversing a LoopStmt node.
     * @param node the LoopingStmt node
     */
    private void exitLoopHelper(LoopingStmt node)
    {
        // Patch any addresses required for exit statements.
        LoopData ld = this.loopInfoList.remove(this.loopInfoList.size() - 1);
        if (ld.loop != node) throw new CodeGenErrorException("Loop info does not match loop!");
        for (Short s : ld.patchAddresses) this.writeMemoryHelper(s, this.startMSP);
    }

    /**
     * Writes value to Machine.memory[addr]. Does not update
     * MSP, PC, and MLP.
     * @param addr address in machine memory to write the value to
     * @param value the value to write to machine memory
     */
    private void writeMemoryHelper(short addr, short value)
    {
        try
        {
           Machine.writeMemory(addr, value);
        } catch (MemoryAddressException e)
        {
            throw new CodeGenErrorException("out of machine memory");
        }
    }

    /**
     * Writes value to Machine.memory[startMSP]. Also increments
     * startMSP by one.
     * @param value the value to write to machine memory
     * @return the memory address the value was written to
     */
    private short writeNextMemoryAddr(short value)
    {
        short retval = this.startMSP;
        this.writeMemoryHelper(this.startMSP++, value);
        return retval;
    }

    /**
     * Set the startAddr in the GenData at the top of the stack to {@code addr} if it has
     * not been set yet.
     * @param addr the address to set startAddr to
     */
    private void setStartAddrIfEmpty(short addr)
    {
        if (this.genDataStack.peek().startAddr == (short)-1)
        {
            this.genDataStack.peek().startAddr = addr;
        }
    }
}
