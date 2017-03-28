package origami.code;

import origami.lang.OEnv;
import origami.nez.ast.LocaleFormat;
import origami.nez.ast.SourcePosition;
import origami.util.ODebug;
import origami.util.OLog;

public class OWarningCode extends OParamCode<OLog> implements OWrapperCode {

	public OWarningCode(OCode node, int level, LocaleFormat fmt, Object... args) {
		super(new OLog(null, level, fmt, args), node.getType(), node);

	}

	public OWarningCode(OCode node, LocaleFormat fmt, Object... args) {
		super(new OLog(null, OLog.Warning, fmt, args), node.getType(), node);

	}

	public OWarningCode(OCode node, OLog m) {
		super(m, node.getType(), node);
	}

	@Override
	public OCode wrapped() {
		return this.getFirst();
	}

	@Override
	public void wrap(OCode code) {
		ODebug.NotAvailable(this);
	}

	public OLog getLog() {
		return this.getHandled();
	}

	@Override
	public OCode setSourcePosition(SourcePosition s) {
		super.setSourcePosition(s);
		this.getLog().setSourcePosition(s);
		return this;
	}

	@Override
	public Object eval(OEnv env) throws Throwable {
		OLog.report(env, this.getLog());
		return this.getParams()[0].eval(env);
	}

	@Override
	public void generate(OGenerator gen) {
		gen.pushWarning(this);
	}

}