package compiler488.symbol;

/**
 * The logical address contains the lexical level where a symbol was declared
 * and an order number representing the offset in words from the
 * base address of the activation record where it is stored.
 * For symbols representing a routine the logical address is defined differently.
 * The lexical level is for the routine itself rather than its declaration.
 * For example if the declaration is in a routine at lexical level 2 then the
 * lexical level in the logical address of the routine is 2+1 = 3.
 * The order number for a routine is the address of its first instruction.
 * @author James Yuan
 */
public class LogicalAddress
{
    private final short lexicLevel;
    private short orderNumber;

    LogicalAddress(short lexicLevel, short orderNumber)
    {
        this.lexicLevel = lexicLevel;
        this.orderNumber = orderNumber;
    }

    public short getLexicLevel()
    {
        return lexicLevel;
    }

    public short getOrderNumber()
    {
        return orderNumber;
    }

    public void setOrderNumber(short orderNumber)
    {
        this.orderNumber = orderNumber;
    }
}
