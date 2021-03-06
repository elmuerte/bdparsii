/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package com.mpobjects.bdparsii.eval;

import java.math.BigDecimal;
import java.math.MathContext;

import ch.obermuhlner.math.big.BigDecimalMath;

/**
 * Represents a binary operation.
 * <p>
 * A binary operation has two sub-expressions. A set of supported operations is also defined. If both arguments are
 * constant, simplifying this expression will again lead to a constant expression.
 */
public final class BinaryOperation extends AbstractExpression {

    /**
     * Enumerates the operations supported by this expression.
     */
    public enum Op {
        ADD(3), SUBTRACT(3), MULTIPLY(4), DIVIDE(4), MODULO(4), POWER(5), LT(2), LT_EQ(2), EQ(2), GT_EQ(2), GT(2),
        NEQ(2), AND(1), OR(1);

        private final int priority;

        Op(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }

    private static final BigDecimal POW_LOWER_LIMT = BigDecimal.valueOf(-999999999);
    private static final BigDecimal POW_UPPER_LIMT = BigDecimal.valueOf(999999999);

    /**
     * power function which uses the JRE's {@link BigDecimal#pow(int, MathContext)} when <code>y</code> is an integer
     * within range. Otherwise use {@link BigDecimalMath#pow(BigDecimal, BigDecimal, MathContext)}.
     *
     * @param x the {@link BigDecimal} value to take to the power
     * @param y the {@link BigDecimal} value to serve as exponent
     * @param mathContext the {@link MathContext} used for the result
     * @return the calculated x to the power of y with the precision specified in the <code>mathContext</code>
     */
    public static BigDecimal pow(BigDecimal x, BigDecimal y, MathContext mathContext) {
        if (POW_LOWER_LIMT.compareTo(y) < 0 && POW_UPPER_LIMT.compareTo(y) > 0) {
            try {
                int intval = y.intValueExact();
                return x.pow(intval, mathContext);
            } catch (ArithmeticException e) {
                // ignore
            }
        }
        return BigDecimalMath.pow(x, y, MathContextGuard.getSafeContext(mathContext));
    }

    private final Op op;
    private Expression left;
    private Expression right;
    private boolean sealed = false;

    /**
     * Creates a new binary operator for the given operator and operands.
     *
     * @param mathContext the math context
     * @param op the operator of the operation
     * @param left the left operand
     * @param right the right operand
     */
    public BinaryOperation(MathContext mathContext, Op op, Expression left, Expression right) {
        super(mathContext);
        this.op = op;
        this.left = left;
        this.right = right;
    }

    /**
     * Returns the operation performed by this binary operation.
     *
     * @return the operation performed
     */
    public Op getOp() {
        return op;
    }

    /**
     * Returns the left operand
     *
     * @return the left operand of this operation
     */
    public Expression getLeft() {
        return left;
    }

    /**
     * Replaces the left operand of the operation with the given expression.
     *
     * @param left the new expression to be used as left operand
     */
    public void setLeft(Expression left) {
        this.left = left;
    }

    /**
     * Returns the right operand
     *
     * @return the right operand of this operation
     */
    public Expression getRight() {
        return right;
    }

    /**
     * Marks an operation as sealed, meaning that re-ordering or operations on the same level must not be re-ordered.
     * <p>
     * Binary operations are sealed if they're e.g. surrounded by braces.
     */
    public void seal() {
        sealed = true;
    }

    /**
     * Determines if the operation is sealed and operands must not be re-ordered.
     *
     * @return <tt>true</tt> if the operation is protected by braces and operands might not be exchanged with operations
     *         nearby.
     */
    public boolean isSealed() {
        return sealed;
    }

    @Override
    @SuppressWarnings({ "squid:S3776", "squid:MethodCyclomaticComplexity" })
    public BigDecimal evaluate(MathContext mathContext) {
        BigDecimal a = left.evaluate(mathContext);
        BigDecimal b = right.evaluate(mathContext);

        if (a == null) {
            throw new ArithmeticException("Left argument evaluated to null");
        }
        if (b == null) {
            throw new ArithmeticException("Right argument evaluated to null");
        }

        switch (op) {
            case ADD:
                return a.add(b, mathContext);
            case SUBTRACT:
                return a.subtract(b, mathContext);
            case MULTIPLY:
                return a.multiply(b, mathContext);
            case DIVIDE:
                return a.divide(b, mathContext);
            case POWER:
                return pow(a, b, mathContext);
            case MODULO:
                return a.remainder(b, mathContext);
            case LT:
                return a.compareTo(b) < 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case LT_EQ:
                return a.compareTo(b) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case GT:
                return a.compareTo(b) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case GT_EQ:
                return a.compareTo(b) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case EQ:
                return a.compareTo(b) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case NEQ:
                return a.compareTo(b) != 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            case AND:
                return a.compareTo(BigDecimal.ZERO) != 0 && b.compareTo(BigDecimal.ZERO) != 0 ? BigDecimal.ONE
                        : BigDecimal.ZERO;
            case OR:
                return a.compareTo(BigDecimal.ZERO) != 0 || b.compareTo(BigDecimal.ZERO) != 0 ? BigDecimal.ONE
                        : BigDecimal.ZERO;
            default:
                throw new UnsupportedOperationException(String.valueOf(op));
        }
    }

    @Override
    public Expression simplify(MathContext mathContext) {
        left = left.simplify(mathContext);
        right = right.simplify(mathContext);
        // First of all we check of both sides are constant. If true, we can directly evaluate the result...
        if (left.isConstant() && right.isConstant()) {
            return new Constant(evaluate());
        }
        // + and * are commutative and associative, therefore we can reorder operands as we desire
        if (op == Op.ADD || op == Op.MULTIPLY) {
            // We prefer the have the constant part at the left side, re-order if it is the other way round.
            // This simplifies further optimizations as we can concentrate on the left side
            if (right.isConstant()) {
                Expression tmp = right;
                right = left;
                left = tmp;
            }

            if (right instanceof BinaryOperation) {
                Expression childOp = trySimplifyRightSide();
                if (childOp != null) {
                    return childOp;
                }
            }
        }

        return this;
    }

    private Expression trySimplifyRightSide() {
        BinaryOperation childOp = (BinaryOperation) right;
        if (op != childOp.op) {
            return null;
        }

        // We have a sub-operation with the same operator, let's see if we can pre-compute some constants.
        if (left.isConstant() && childOp.left.isConstant()) {
            // Left side is constant, we therefore can combine constants. We can rely on the constant
            // being on the left side, since we reorder commutative operations (see above)
            if (op == Op.ADD) {
                return new BinaryOperation(getMathContext(),
                                           op,
                                           new Constant(left.evaluate().add(childOp.left.evaluate())),
                                           childOp.right);
            }
            if (op == Op.MULTIPLY) {
                return new BinaryOperation(getMathContext(),
                                           op,
                                           new Constant(left.evaluate().multiply(childOp.left.evaluate())),
                                           childOp.right);
            }
        }

        if (childOp.left.isConstant()) {
            // Since our left side is non constant, but the left side of the child expression is,
            // we push the constant up, to support further optimizations
            return new BinaryOperation(getMathContext(),
                                       op,
                                       childOp.left,
                                       new BinaryOperation(getMathContext(), op, left, childOp.right));
        }

        return null;
    }

    @Override
    public String toString() {
        return "(" + left.toString() + " " + op + " " + right + ")";
    }
}
