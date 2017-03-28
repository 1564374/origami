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

package origami.code;

import origami.OrigamiContext.LocalVariables;
import origami.lang.OEnv;
import origami.lang.type.OType;
import origami.rule.OFmt;

public class ONameCode extends OParamCode<String> {
	private final boolean readOnly;

	public ONameCode(String name, OType ty, boolean readOnly) {
		super(name, ty);
		this.readOnly = readOnly;
	}

	public ONameCode(String name, OType ty) {
		this(name, ty, true);
	}

	public String getName() {
		return this.getHandled();
	}

	@Override
	public Object eval(OEnv env) throws Throwable {
		LocalVariables vars = env.get(LocalVariables.class);
		return vars.get(this.getName());
	}

	@Override
	public void generate(OGenerator gen) {
		gen.pushName(this);
	}

	@Override
	public OCode newAssignCode(OEnv env, OCode right) {
		if (this.readOnly) {
			throw new OErrorCode(env, OFmt.read_only__YY0, this.getName());
		}
		OType ty = this.getType();
		return new OAssignCode(ty, false, this.getName(), right.asType(env, ty));
	}

}