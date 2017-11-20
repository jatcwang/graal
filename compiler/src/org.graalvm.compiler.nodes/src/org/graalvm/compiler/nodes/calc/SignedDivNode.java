/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;

@NodeInfo(shortName = "/")
public class SignedDivNode extends IntegerDivRemNode implements LIRLowerable {

    public static final NodeClass<SignedDivNode> TYPE = NodeClass.create(SignedDivNode.class);

    public SignedDivNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected SignedDivNode(NodeClass<? extends SignedDivNode> c, ValueNode x, ValueNode y) {
        super(c, IntegerStamp.OPS.getDiv().foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT)), Op.DIV, Type.SIGNED, x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(IntegerStamp.OPS.getDiv().foldStamp(getX().stamp(NodeView.DEFAULT), getY().stamp(NodeView.DEFAULT)));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        if (forX.isConstant() && forY.isConstant()) {
            @SuppressWarnings("hiding")
            long y = forY.asJavaConstant().asLong();
            if (y == 0) {
                return this; // this will trap, can not canonicalize
            }
            return ConstantNode.forIntegerStamp(stamp(view), forX.asJavaConstant().asLong() / y);
        } else if (forY.isConstant()) {
            long c = forY.asJavaConstant().asLong();
            ValueNode v = canonical(forX, c, view);
            if (v != null) {
                return v;
            }
        }

        // Convert the expression ((a - a % b) / b) into (a / b).
        if (forX instanceof SubNode) {
            SubNode integerSubNode = (SubNode) forX;
            if (integerSubNode.getY() instanceof SignedRemNode) {
                SignedRemNode integerRemNode = (SignedRemNode) integerSubNode.getY();
                if (integerSubNode.stamp(view).isCompatible(this.stamp(view)) && integerRemNode.stamp(view).isCompatible(this.stamp(view)) && integerSubNode.getX() == integerRemNode.getX() &&
                                forY == integerRemNode.getY()) {
                    SignedDivNode sd = new SignedDivNode(integerSubNode.getX(), forY);
                    sd.stateBefore = this.stateBefore;
                    return sd;
                }
            }
        }

        if (next() instanceof SignedDivNode) {
            NodeClass<?> nodeClass = getNodeClass();
            if (next().getClass() == this.getClass() && nodeClass.equalInputs(this, next()) && valueEquals(next())) {
                return next();
            }
        }

        return this;
    }

    public static ValueNode canonical(ValueNode forX, long c, NodeView view) {
        if (c == 1) {
            return forX;
        }
        if (c == -1) {
            return NegateNode.create(forX, view);
        }
        long abs = Math.abs(c);
        if (CodeUtil.isPowerOf2(abs) && forX.stamp(view) instanceof IntegerStamp) {
            ValueNode dividend = forX;
            IntegerStamp stampX = (IntegerStamp) forX.stamp(view);
            int log2 = CodeUtil.log2(abs);
            // no rounding if dividend is positive or if its low bits are always 0
            if (stampX.canBeNegative() || (stampX.upMask() & (abs - 1)) != 0) {
                int bits = PrimitiveStamp.getBits(forX.stamp(view));
                RightShiftNode sign = new RightShiftNode(forX, ConstantNode.forInt(bits - 1));
                UnsignedRightShiftNode round = new UnsignedRightShiftNode(sign, ConstantNode.forInt(bits - log2));
                dividend = BinaryArithmeticNode.add(dividend, round, view);
            }
            RightShiftNode shift = new RightShiftNode(dividend, ConstantNode.forInt(log2));
            if (c < 0) {
                return NegateNode.create(shift, view);
            }
            return shift;
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitDiv(gen.operand(getX()), gen.operand(getY()), gen.state(this)));
    }
}
