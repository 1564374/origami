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

package origami.rule;

import java.math.BigDecimal;
import java.math.BigInteger;

import origami.OEnv;
import origami.OLog.Messenger;
import origami.code.NullCode;
import origami.code.OCode;
import origami.code.OValueCode;
import origami.nez.ast.Tree;
import origami.trait.OImportable;
import origami.trait.OStringUtils;
import origami.trait.OTypeRule;

public class LiteralRules implements OImportable, TypeAnalysis {

	public OTypeRule NullExpr = new AbstractTypeRule() {
		@Override
		public OCode typeRule(OEnv env, Tree<?> t) {
			return new NullCode(env);
		}
	};

	public OTypeRule TrueExpr = new ValueRule(true, boolean.class);
	public OTypeRule FalseExpr = new ValueRule(false, boolean.class);

	public OTypeRule NumberExpr = new NumberRule(double.class);
	public OTypeRule ByteExpr = new NumberRule(byte.class);
	public OTypeRule ShortExpr = new NumberRule(short.class);
	public OTypeRule IntExpr = new NumberRule(int.class);
	public OTypeRule LongExpr = new NumberRule(long.class);
	public OTypeRule FloatExpr = new NumberRule(float.class);
	public OTypeRule DoubleExpr = new NumberRule(double.class);

	public OTypeRule CharExpr = new AbstractTypeRule() {
		@Override
		public OCode typeRule(OEnv env, Tree<?> t) {
			String s = OStringUtils.unquoteString(t.toText());
			Object v = null;
			if (s.length() == 1) {
				v = s.charAt(0);
			} else {
				v = s;
			}
			return env.v(v);
		}

	};

	public OTypeRule StringExpr = new AbstractTypeRule() {
		@Override
		public OCode typeRule(OEnv env, Tree<?> t) {
			return env.v(OStringUtils.unquoteString(t.toText()));
		}

	};

	public static class ValueRule extends AbstractTypeRule {
		public final Object value;
		public final Class<?> baseType;

		public ValueRule(Object value, Class<?> baseType) {
			this.value = value;
			this.baseType = baseType;
		}

		@Override
		public OCode typeRule(OEnv env, Tree<?> t) {
			return new OValueCode(this.value, env.t(this.baseType));
		}
	}

	public static class NumberRule extends AbstractTypeRule implements TypeAnalysis {
		public final Class<?> baseType;

		public NumberRule(Class<?> baseType) {
			this.baseType = baseType;
		}

		@Override
		public OCode typeRule(OEnv env, Tree<?> t) {
			Messenger m = new Messenger();
			String text = t.toText().replace("_", "");
			int radix = 10;
			if (text.endsWith("L") || text.endsWith("l")) {
				text = text.substring(0, text.length() - 1);
			}
			if (text.startsWith("0b") || text.startsWith("0B")) {
				text = text.substring(2);
				radix = 2;
			} else if (text.startsWith("0x") || text.startsWith("0X")) {
				text = text.substring(2);
				radix = 16;
			} else if (text.startsWith("0")) {
				radix = 8;
			}
			Class<?> base = baseType;
			Object value = null;
			if (base == int.class) {
				try {
					value = Integer.parseInt(text, radix);
				} catch (NumberFormatException e) {
					m.reportWarning(t, "wrong number format %s by %s", text, e);
					value = 0;
				}
			} else if (base == double.class) {
				try {
					value = Double.parseDouble(text);
				} catch (NumberFormatException e) {
					m.reportWarning(t, "wrong number format %s by %s", text, e);
					value = 0.0;
				}
			} else if (base == long.class) {
				try {
					value = Long.parseLong(text, radix);
				} catch (NumberFormatException e) {
					m.reportWarning(t, "wrong number format %s by %s", text, e);
					value = 0L;
				}
			} else if (base == float.class) {
				try {
					value = Float.parseFloat(text);
				} catch (NumberFormatException e) {
					m.reportWarning(t, "wrong number format %s by %s", text, e);
					value = 0.0f;
				}
			} else if (base == BigInteger.class) {
				try {
					value = new BigInteger(text, radix);
				} catch (NumberFormatException e2) {
					m.reportWarning(t, "wrong number format %s by %s", text, e2);
					value = BigInteger.ZERO;
				}
			} else {
				try {
					value = new BigDecimal(text);
				} catch (NumberFormatException e2) {
					m.reportWarning(t, "wrong number format %s by %s", text, e2);
					value = BigDecimal.ZERO;
				}
			}
			OCode code = new OValueCode(value, env.t(base));
			return m.newMessageCode(code);
		}
	}

}
