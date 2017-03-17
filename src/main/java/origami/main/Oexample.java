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
import java.util.ArrayList;
import java.util.HashMap;

import origami.OConsole;
import origami.main.Otest.Coverage;
import origami.nez.ast.Source;
import origami.nez.ast.Symbol;
import origami.nez.ast.Tree;
import origami.nez.parser.CommonSource;
import origami.nez.parser.Parser;
import origami.nez.parser.ParserFactory;
import origami.nez.parser.ParserFactory.TreeWriter;
import origami.nez.peg.GrammarLoader;
import origami.nez.peg.OGrammar;

public class Oexample extends OCommand {
	HashMap<String, Parser> parserMap = new HashMap<>();
	HashMap<String, Long> timeMap = new HashMap<>();
	TreeWriter treeWriter = null;

	Coverage cov = null;
	String desc = "";
	int tested = 0;
	int succ = 0;

	@Override
	public void exec(ParserFactory fac) throws IOException {
		treeWriter = fac.newTreeWriter(origami.main.tool.AbstractSyntaxTreeWriter.class);
		if (this instanceof Otest || fac.is("cov", true)) {
			cov = new Coverage();
			cov.init(fac, fac.getGrammar());
		}
		loadExample(fac);
		if (tested > 0) {
			double passRatio = (double) succ / tested;
			if (cov != null) {
				begin(Note);
				cov.dump(fac);
				end();
				double fullcov = cov.cov();
				p(bold("Result: %.2f%% passed, %.2f%% (coverage) tested."), (passRatio * 100), (fullcov * 100));
				if (tested == succ && fullcov > 0.5) {
					p("");
					p(bold("Congratulation!!"));
					p("You are invited to share your grammar at Nez open grammar repository, ");
					p(" http://github.com/nez-peg/grammar.");
					p("If you want, please send a pull-request with:");
					p(bold("git commit -am '" + desc + ", %.2f%% (coverage) tested.'"), (fullcov * 100));
				}
			} else {
				p(bold("Result: %.2f%% passed."), (passRatio * 100));
			}
		}
	}

	void loadExample(ParserFactory fac) throws IOException {
		String path = fac.value("grammar", null);
		if (path == null) {
			exit(1, "specified grammar");
		}
		Source s = CommonSource.newFileSource(path, fac.list("grammar-path"));
		importFile(fac, null, s);
		desc = parseGrammarDescription(s);
	}

	void importFile(ParserFactory fac, String prefix, Source s) throws IOException {
		Tree<?> t = OGrammar.NezParser.parse(s);
		if (t.is(GrammarLoader._Source)) {
			for (Tree<?> sub : t) {
				parse(fac, prefix, sub);
			}
		}
	}

	public final static Symbol _Example = Symbol.unique("Example");
	public final static Symbol _hash = Symbol.unique("hash"); // example
	public final static Symbol _name2 = Symbol.unique("name2"); // example
	public final static Symbol _text = Symbol.unique("text"); // example

	String prefix(String prefix, String name) {
		return prefix == null ? name : prefix + "." + name;
	}

	void parse(ParserFactory factory, String prefix, Tree<?> node) throws IOException {
		if (node.is(GrammarLoader._Production)) {
			return;
		}
		if (node.is(_Example)) {
			parseExample(factory, prefix, node);
			return;
		}
		if (node.is(GrammarLoader._Grammar)) {
			String name = node.getText(GrammarLoader._name, null);
			Tree<?> body = node.get(GrammarLoader._body);
			for (Tree<?> sub : body) {
				parse(factory, prefix(prefix, name), sub);
			}
			return;
		}
		if (node.is(GrammarLoader._Import)) {
			String name = node.getText(GrammarLoader._name, null);
			String path = name;
			if (!name.startsWith("/") && !name.startsWith("\\")) {
				path = GrammarLoader.extractFilePath(node.getSource().getResourceName()) + "/" + name;
			}
			importFile(factory, prefix, CommonSource.newFileSource(path, null));
			return;
		}
	}

	public void parseExample(ParserFactory factory, String prefix, Tree<?> node) throws IOException {
		Tree<?> nameNode = node.get(GrammarLoader._name, null);
		String uname = uname(prefix, nameNode.toText());
		Parser p = this.getParser(factory, nameNode, uname);
		if (p != null) {
			performExample(p, uname, node);
		}
		if (this instanceof Otest) {
			nameNode = node.get(_name2, null);
			if (nameNode != null) {
				uname = uname(prefix, nameNode.toText());
				p = this.getParser(factory, nameNode, uname);
				if (p != null) {
					performExample(p, uname, node);
				}
			}
		}
	}

	private Parser getParser(ParserFactory factory, Tree<?> nameNode, String uname) throws IOException {
		Parser p = this.parserMap.get(uname);
		if (p == null) {
			ParserFactory fac = factory.newFactory(factory.getGrammar());
			p = fac.newParser(uname);
			if (p == null) {
				factory.reportError(nameNode, "undefined nonterminal: %s", uname);
				return null;
			}
			p.setPrintingException(true);
			this.parserMap.put(uname, p);
		}
		return p;
	}

	String uname(String prefix, String name) {
		if (name.indexOf('.') > 0) {
			return name;
		}
		return prefix(prefix, name);
	}

	protected void performExample(Parser p, String uname, Tree<?> ex) {
		Tree<?> textNode = ex.get(_text);
		Source s = newSource(textNode, p.getFactory());
		String name = uname + " (" + textNode.getSource().getResourceName() + ":" + textNode.getLineNum() + ")";
		try {
			tested++;
			long t1 = System.nanoTime();
			Tree<?> node = p.parse(s);
			succ++;
			long t2 = System.nanoTime();
			p(Green, "[PASS] " + name);
			if (!(this instanceof Otest)) {
				if (node != null) {
					OConsole.dump(" ", bold(textNode.toText()));
					display(p.getFactory(), treeWriter, node);
				}
			}
			record(uname, t2 - t1);
		} catch (IOException e) {
			p(Red, "[FAIL] " + name);
			p(Red, bold(textNode.toText()));
		} catch (Throwable e) {
			p(Red, "[FAIL] " + name);
			p(Red, bold(textNode.toText()));
			p.getFactory().trace(e);
		}
	}

	private void record(String uname, long t) {
		Long l = this.timeMap.get(uname);
		if (l == null) {
			l = t;
		} else {
			l += t;
		}
		this.timeMap.put(uname, l);
	}

	private Source newSource(Tree<?> textNode, ParserFactory fac) {
		// byte[] b = parseBinary(textNode.toText());
		// if (b != null) {
		// fac.verbose("binary example: %s", textNode.toText());
		// return new StringSource(textNode.getSource().getResourceName(),
		// textNode.getSourcePosition(), b, true);
		// }
		return textNode.toSource();
	}

	private byte[] parseBinary(String t) {
		ArrayList<Byte> bytes = new ArrayList<>(t.length());
		for (int i = 0; i < t.length(); i++) {
			char ch = t.charAt(i);
			if (ch == ' ' || ch == '\n' || ch == '\r') {
				continue;
			}
			int b = parseHex(t, i);
			if (b != -1) {
				// System.out.println("hex=" + b);
				i += 1;
				bytes.add((byte) b);
				continue;
			}
		}
		bytes.add((byte) 0);
		byte[] b = new byte[bytes.size()];
		for (int i = 0; i < b.length; i++) {
			b[i] = bytes.get(i);
		}
		return b;
	}

	private int parseHex(String t, int i) {
		try {
			char ch = t.charAt(i);
			char ch2 = t.charAt(i + 1);
			return Integer.parseInt("" + ch + ch2, 16);

		} catch (Exception e) {
			return -1;
		}
	}

	public final static String parseGrammarDescription(Source sc) {
		StringBuilder sb = new StringBuilder();
		long pos = 0;
		boolean found = false;
		for (; pos < sc.length(); pos++) {
			int ch = sc.byteAt(pos);
			if (Character.isAlphabetic(ch)) {
				found = true;
				break;
			}
		}
		if (found) {
			for (; pos < sc.length(); pos++) {
				int ch = sc.byteAt(pos);
				if (ch == '\n' || ch == '\r' || ch == '-' || ch == '*') {
					break;
				}
				sb.append((char) ch);
			}
		}
		return sb.toString().trim();
	}

}