/***********************************************************************
 * Copyright 2017 Kimio Kuramitsu and ORIGAMI project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***********************************************************************/

package origami.nez.parser;

import java.util.ArrayList;
import java.util.List;

import origami.nez.parser.ParserCode.MemoPoint;
import origami.nez.peg.Expression;
import origami.nez.peg.ExpressionVisitor;
import origami.nez.peg.NezFunc;
import origami.nez.peg.OGrammar;
import origami.nez.peg.OProduction;
import origami.nez.peg.Typestate;
import origami.trait.OStringUtils;

public class NZ86Compiler implements ParserFactory.Compiler {

	public final static NZ86Compiler newCompiler(ParserFactory strategy) {
		return new NZ86Compiler();
	}

	public NZ86Compiler() {
	}

	// Local Option
	boolean BinaryGrammar = false;
	boolean TreeConstruction = true;
	boolean Optimization = true;

	@Override
	public NZ86Code compile(ParserFactory factory, OGrammar grammar) {
		NZ86Code code = new NZ86Code(factory, grammar, factory);
		this.TreeConstruction = factory.TreeOption();
		if (factory.MemoOption()) {
			code.initMemoPoint(factory);
		}
		new CompilerVisitor(factory, code, grammar).compileAll();
		// code.dump();
		return code;
	}

	class CompilerVisitor extends ExpressionVisitor<NZ86Instruction, NZ86Instruction> {

		final NZ86Code code;
		final OGrammar grammar;
		final ParserFactory factory;

		CompilerVisitor(ParserFactory factory, NZ86Code code, OGrammar grammar) {
			this.code = code;
			this.grammar = grammar;
			this.factory = factory;
		}

		private NZ86Code compileAll() {
			for (OProduction p : grammar) {
				this.compileProduction(code.codeList(), p, new NZ86.Ret());
			}
			for (NZ86Instruction inst : code.codeList()) {
				if (inst instanceof NZ86.Call) {
					NZ86.Call call = (NZ86.Call) inst;
					if (call.jump == null) {
						call.jump = call.next;
						call.next = NZ86.joinPoint(code.getInstruction(call.uname));// f.getCompiled();
					}
				}
			}
			return code;
		}

		protected void compileProduction(List<NZ86Instruction> codeList, OProduction p, NZ86Instruction next) {
			MemoPoint memoPoint = code.getMemoPoint(p.getUniqueName());
			next = compileProductionExpression(memoPoint, p.getExpression(), next);
			code.setInstruction(p.getUniqueName(), next);
			NZ86Instruction block = new NZ86.Nop(p.getUniqueName(), next);
			layoutCode(codeList, block);
		}

		private NZ86Instruction compileProductionExpression(MemoPoint memoPoint, Expression p, NZ86Instruction next) {
			if (memoPoint != null) {
				if (memoPoint.typeState == Typestate.Unit) {
					NZ86Instruction memo = new NZ86.Memo(memoPoint, next);
					NZ86Instruction inside = compile(p, memo);
					NZ86Instruction failmemo = new NZ86.MemoFail(memoPoint);
					inside = new NZ86.Alt(failmemo, inside);
					return new NZ86.Lookup(memoPoint, inside, next);
				} else {
					NZ86Instruction memo = new NZ86.TMemo(memoPoint, next);
					NZ86Instruction inside = compile(p, memo);
					NZ86Instruction failmemo = new NZ86.MemoFail(memoPoint);
					inside = new NZ86.Alt(failmemo, inside);
					return new NZ86.TLookup(memoPoint, inside, next);
				}
			}
			return compile(p, next);
		}

		private void layoutCode(List<NZ86Instruction> codeList, NZ86Instruction inst) {
			if (inst == null) {
				return;
			}
			if (inst.id == -1) {
				inst.id = codeList.size();
				codeList.add(inst);
				layoutCode(codeList, inst.next);
				// if (inst.next != null && inst.id + 1 != inst.next.id) {
				// MozInst.joinPoint(inst.next);
				// }
				layoutCode(codeList, inst.branch());
				if (inst instanceof NZ86.Dispatch) {
					NZ86.Dispatch match = (NZ86.Dispatch) inst;
					for (int ch = 0; ch < match.jumpTable.length; ch++) {
						layoutCode(codeList, match.jumpTable[ch]);
					}
				}
			}
		}

		// encoding

		private NZ86Instruction compile(Expression e, NZ86Instruction next) {
			return e.visit(this, next);
		}

		@Override
		public NZ86Instruction visitEmpty(Expression.PEmpty p, NZ86Instruction next) {
			return next;
		}

		private final NZ86Instruction commonFailure = new NZ86.Fail();

		public NZ86Instruction fail(Expression e) {
			return this.commonFailure;
		}

		@Override
		public NZ86Instruction visitFail(Expression.PFail p, NZ86Instruction next) {
			return this.commonFailure;
		}

		@Override
		public NZ86Instruction visitByte(Expression.PByte p, NZ86Instruction next) {
			if (BinaryGrammar && p.byteChar == 0) {
				return new NZ86.BinaryByte(next);
			}
			return new NZ86.Byte(p.byteChar, next);
		}

		@Override
		public NZ86Instruction visitByteSet(Expression.PByteSet p, NZ86Instruction next) {
			boolean[] b = p.byteSet();
			if (BinaryGrammar && b[0]) {
				return new NZ86.BinarySet(b, next);
			}
			return new NZ86.Set(b, next);
		}

		@Override
		public NZ86Instruction visitAny(Expression.PAny p, NZ86Instruction next) {
			return new NZ86.Any(next);
		}

		@Override
		public final NZ86Instruction visitNonTerminal(Expression.PNonTerminal n, NZ86Instruction next) {
			OProduction p = n.getProduction();
			return new NZ86.Call(p.getUniqueName(), next);
		}

		private byte[] toMultiChar(Expression e) {
			ArrayList<Integer> l = new ArrayList<>();
			Expression.extractString(e, l);
			byte[] utf8 = new byte[l.size()];
			for (int i = 0; i < l.size(); i++) {
				utf8[i] = (byte) (int) l.get(i);
			}
			return utf8;
		}

		@Override
		public final NZ86Instruction visitOption(Expression.POption p, NZ86Instruction next) {
			if (Optimization) {
				Expression inner = getInnerExpression(p);
				if (inner instanceof Expression.PByte) {
					if (BinaryGrammar && ((Expression.PByte) inner).byteChar == 0) {
						return new NZ86.BinaryOByte(next);
					}
					return new NZ86.OByte(((Expression.PByte) inner).byteChar, next);
				}
				if (inner instanceof Expression.PByteSet) {
					boolean[] b = ((Expression.PByteSet) inner).byteSet();
					if (BinaryGrammar && b[0]) {
						return new NZ86.BinaryOSet(b, next);
					}
					return new NZ86.OSet(b, next);
				}
				if (Expression.isString(inner)) {
					byte[] utf8 = toMultiChar(inner);
					return new NZ86.OStr(utf8, next);
				}
			}
			NZ86Instruction pop = new NZ86.Succ(next);
			return new NZ86.Alt(next, compile(p.get(0), pop));
		}

		@Override
		public NZ86Instruction visitRepetition(Expression.PRepetition p, NZ86Instruction next) {
			NZ86Instruction next2 = this.compileRepetition(p, next);
			if (p.isOneMore()) {
				next2 = compile(p.get(0), next2);
			}
			return next2;
		}

		private NZ86Instruction compileRepetition(Expression.PRepetition p, NZ86Instruction next) {
			if (Optimization) {
				Expression inner = getInnerExpression(p);
				if (inner instanceof Expression.PByte) {
					if (BinaryGrammar && ((Expression.PByte) inner).byteChar == 0) {
						return new NZ86.BinaryRByte(next);
					}
					return new NZ86.RByte(((Expression.PByte) inner).byteChar, next);
				}
				if (inner instanceof Expression.PByteSet) {
					boolean[] b = ((Expression.PByteSet) inner).byteSet();
					if (BinaryGrammar && b[0]) {
						return new NZ86.BinaryRSet(b, next);
					}
					return new NZ86.RSet(b, next);
				}
				if (Expression.isString(inner)) {
					byte[] utf8 = toMultiChar(inner);
					return new NZ86.RStr(utf8, next);
				}
			}
			NZ86Instruction skip = new NZ86.Step();
			NZ86Instruction start = compile(p.get(0), skip);
			skip.next = NZ86.joinPoint(start);
			return new NZ86.Alt(next, start);
		}

		@Override
		public NZ86Instruction visitAnd(Expression.PAnd p, NZ86Instruction next) {
			NZ86Instruction inner = compile(p.get(0), new NZ86.Back(next));
			return new NZ86.Pos(inner);
		}

		@Override
		public final NZ86Instruction visitNot(Expression.PNot p, NZ86Instruction next) {
			if (Optimization) {
				Expression inner = getInnerExpression(p);
				if (inner instanceof Expression.PByte) {
					if (BinaryGrammar && ((Expression.PByte) inner).byteChar != 0) {
						return new NZ86.BinaryNByte(((Expression.PByte) inner).byteChar, next);
					}
					return new NZ86.NByte(((Expression.PByte) inner).byteChar, next);
				}
				if (inner instanceof Expression.PByteSet) {
					boolean[] b = ((Expression.PByteSet) inner).byteSet();
					if (BinaryGrammar && !b[0]) {
						return new NZ86.BinaryNSet(b, next);
					}
					return new NZ86.NSet(b, next);
				}
				if (inner instanceof Expression.PAny) {
					return new NZ86.NAny(next);
				}
				if (Expression.isString(inner)) {
					byte[] utf8 = toMultiChar(inner);
					return new NZ86.NStr(utf8, next);
				}
			}
			NZ86Instruction fail = new NZ86.Succ(new NZ86.Fail());
			return new NZ86.Alt(next, compile(p.get(0), fail));
		}

		@Override
		public NZ86Instruction visitPair(Expression.PPair p, NZ86Instruction next) {
			NZ86Instruction nextStart = next;
			for (int i = p.size() - 1; i >= 0; i--) {
				Expression e = p.get(i);
				nextStart = compile(e, nextStart);
			}
			return nextStart;
		}

		@Override
		public final NZ86Instruction visitChoice(Expression.PChoice p, NZ86Instruction next) {
			NZ86Instruction nextChoice = compile(p.get(p.size() - 1), next);
			for (int i = p.size() - 2; i >= 0; i--) {
				Expression e = p.get(i);
				nextChoice = new NZ86.Alt(nextChoice, compile(e, new NZ86.Succ(next)));
			}
			return nextChoice;
		}

		@Override
		public final NZ86Instruction visitDispatch(Expression.PDispatch p, NZ86Instruction next) {
			NZ86Instruction[] compiled = new NZ86Instruction[p.size() + 1];
			compiled[0] = commonFailure;
			if (isAllD(p)) {
				for (int i = 0; i < p.size(); i++) {
					compiled[i + 1] = compile(nextD(p.get(i)), next);
				}
				return new NZ86.DDispatch(p.indexMap, compiled);
			} else {
				for (int i = 0; i < p.size(); i++) {
					compiled[i + 1] = compile(p.get(i), next);
				}
				return new NZ86.Dispatch(p.indexMap, compiled);
			}
		}

		private boolean isAllD(Expression.PDispatch p) {
			for (int i = 0; i < p.size(); i++) {
				if (!isD(p.get(i))) {
					return false;
				}
			}
			return true;
		}

		private boolean isD(Expression e) {
			if (e instanceof Expression.PPair) {
				if (e.get(0) instanceof Expression.PAny) {
					return true;
				}
				return false;
			}
			return (e instanceof Expression.PAny);
		}

		private Expression nextD(Expression e) {
			if (e instanceof Expression.PPair) {
				return e.get(1);
			}
			return Expression.defaultEmpty;
		}

		@Override
		public NZ86Instruction visitTree(Expression.PTree p, NZ86Instruction next) {
			if (TreeConstruction) {
				next = new NZ86.TEnd(p.tag, p.value, p.endShift, next);
				next = compile(p.get(0), next);
				if (p.folding) {
					// System.out.println("@@@ folding" + p);
					return new NZ86.TFold(p.label, p.beginShift, next);
				} else {
					return new NZ86.TBegin(p.beginShift, next);
				}
			}
			return compile(p.get(0), next);
		}

		@Override
		public NZ86Instruction visitTag(Expression.PTag p, NZ86Instruction next) {
			if (TreeConstruction) {
				return new NZ86.TTag(p.tag, next);
			}
			return next;
		}

		@Override
		public NZ86Instruction visitReplace(Expression.PReplace p, NZ86Instruction next) {
			if (TreeConstruction) {
				return new NZ86.TReplace(p.value, next);
			}
			return next;
		}

		// Tree

		@Override
		public final NZ86Instruction visitLinkTree(Expression.PLinkTree p, NZ86Instruction next) {
			if (TreeConstruction) {
				next = new NZ86.TLink(p.label, next);
				next = compile(p.get(0), next);
				return new NZ86.TPush(next);
			}
			return compile(p.get(0), next);
		}

		@Override
		public NZ86Instruction visitDetree(Expression.PDetree p, NZ86Instruction next) {
			if (TreeConstruction) {
				next = new NZ86.TPop(next);
				next = compile(p.get(0), next);
				return new NZ86.TPush(next);
			}
			return compile(p.get(0), next);
		}

		/* Symbol */

		@Override
		public NZ86Instruction visitSymbolScope(Expression.PSymbolScope p, NZ86Instruction next) {
			if (p.funcName == NezFunc.block) {
				next = new NZ86.SClose(next);
				next = compile(p.get(0), next);
				return new NZ86.SOpen(next);
			} else {
				next = new NZ86.SClose(next);
				next = compile(p.get(0), next);
				return new NZ86.SMask(p.param, next);
			}
		}

		@Override
		public NZ86Instruction visitSymbolAction(Expression.PSymbolAction p, NZ86Instruction next) {
			return new NZ86.Pos(compile(p.get(0), new NZ86.SDef(p.table, next)));
		}

		@Override
		public NZ86Instruction visitSymbolPredicate(Expression.PSymbolPredicate p, NZ86Instruction next) {
			switch (p.funcName) {
			case exists:
				if (p.symbol == null) {
					return new NZ86.SExists(p.table, next);
				} else {
					return new NZ86.SIsDef(p.table, OStringUtils.utf8(p.symbol), next);
				}
			case is:
				return new NZ86.Pos(compile(p.get(0), new NZ86.SIs(p.table, next)));
			case isa:
				return new NZ86.Pos(compile(p.get(0), new NZ86.SIsa(p.table, next)));
			case match:
				return new NZ86.SMatch(p.table, next);
			default:
				break;
			}
			return next;
		}

		@Override
		public NZ86Instruction visitScan(Expression.PScan p, NZ86Instruction next) {
			return new NZ86.Pos(compile(p.get(0), new NZ86.NScan(p.mask, p.shift, next)));
		}

		@Override
		public NZ86Instruction visitRepeat(Expression.PRepeat p, NZ86Instruction next) {
			NZ86Instruction check = new NZ86.NDec(next, null);
			NZ86Instruction repeated = compile(p.get(0), check);
			check.next = repeated;
			return check;
		}

		@Override
		public NZ86Instruction visitTrap(Expression.PTrap p, NZ86Instruction next) {
			if (p.trapid != -1) {
				return new NZ86.Trap(p.trapid, p.uid, next);
			}
			return next;
		}

		/* Optimization */

		private Expression getInnerExpression(Expression p) {
			return Expression.deref(p.get(0));
		}

		// Unused

		@Override
		public NZ86Instruction visitIf(Expression.PIfCondition e, NZ86Instruction next) {
			factory.verbose("unremoved if condition", e);
			return next;
		}

		@Override
		public NZ86Instruction visitOn(Expression.POnCondition e, NZ86Instruction next) {
			factory.verbose("unremoved on condition", e);
			return compile(e.get(0), next);
		}
	}

}
