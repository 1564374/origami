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

package origami.main;

import java.io.IOException;

import origami.nez.parser.Parser;
import origami.nez.parser.ParserFactory;
import origami.nez.parser.ParserFactory.GrammarWriter;

public class Ccode extends OCommand {

	@Override
	public void exec(ParserFactory fac) throws IOException {
		GrammarWriter grammarWriter = fac.newGrammarWriter(origami.main.tool.CParserGenerator.class);
		if (fac.is("raw", false)) {
			grammarWriter.writeGrammar(fac, fac.getGrammar());
		} else {
			Parser p = fac.newParser();
			grammarWriter.writeGrammar(fac, p.getGrammar());
		}
	}

}
