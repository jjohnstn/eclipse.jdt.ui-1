/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Konstantin Scheglov (scheglov_ke@nlmk.ru) - initial API and implementation
 *          (reports 71244 & 74746: New Quick Assist's [quick assist])
 *   Benjamin Muskalla (buskalla@innoopract.com) - 104021: [quick fix] Introduce
 *   		new local with casted type applied more than once
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.core.runtime.Preferences;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.QuickTemplateProcessor;
import org.eclipse.jdt.internal.ui.text.correction.proposals.CUCorrectionProposal;

public class AdvancedQuickAssistTest extends QuickFixTest {

	private static final Class THIS= AdvancedQuickAssistTest.class;

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	public AdvancedQuickAssistTest(String name) {
		super(name);
	}

	public static Test suite() {
		return setUpTest(new TestSuite(THIS));
	}

	public static Test setUpTest(Test test) {
		return new ProjectTestSetup(test);
	}


	protected void setUp() throws Exception {
		Hashtable options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");

		JavaCore.setOptions(options);

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);
		store.setValue(PreferenceConstants.CODEGEN_KEYWORD_THIS, false);

		StubUtility.setCodeTemplate(CodeTemplateContextType.METHODSTUB_ID, "//TODO\n${body_statement}", null);

		Preferences corePrefs= JavaPlugin.getJavaCorePluginPreferences();
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_PREFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_FIELD_SUFFIXES, "");
		corePrefs.setValue(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, "");

		fJProject1= ProjectTestSetup.getProject();

		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}


	protected void tearDown() throws Exception {
		JavaProjectHelper.clear(fJProject1, ProjectTestSetup.getDefaultClasspath());
	}

	public void testSplitIfCondition1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a && (b == 0)) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("&&");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a) {\n");
		buf.append("            if (b == 0) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testSplitIfCondition2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0) && c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("&& (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a) {\n");
		buf.append("            if (b == 0 && c) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testSplitIfCondition3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0) && c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("&& c");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0)) {\n");
		buf.append("            if (c) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testSplitIfElseCondition() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a && (b == 0)) {\n");
		buf.append("            b= 9;\n");
		buf.append("        } else {\n");
		buf.append("            b= 2;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("&&");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertNumberOfProposals(proposals, 5);
		assertCorrectLabels(proposals);

		ArrayList previews= new ArrayList();
		ArrayList expecteds= new ArrayList();
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a) {\n");
		buf.append("            if (b == 0) {\n");
		buf.append("                b= 9;\n");
		buf.append("            } else {\n");
		buf.append("                b= 2;\n");
		buf.append("            }\n");
		buf.append("        } else {\n");
		buf.append("            b= 2;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		addPreviewAndExpected(proposals, buf, expecteds, previews);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if ((b == 0) && a) {\n");
		buf.append("            b= 9;\n");
		buf.append("        } else {\n");
		buf.append("            b= 2;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		addPreviewAndExpected(proposals, buf, expecteds, previews);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        boolean c = a && (b == 0);\n");
		buf.append("        if (c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        } else {\n");
		buf.append("            b= 2;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		addPreviewAndExpected(proposals, buf, expecteds, previews);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        boolean c = a && (b == 0);\n");
		buf.append("        if (c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        } else {\n");
		buf.append("            b= 2;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		addPreviewAndExpected(proposals, buf, expecteds, previews);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if ((a && (b == 0))) {\n");
		buf.append("            b= 9;\n");
		buf.append("        } else {\n");
		buf.append("            b= 2;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		addPreviewAndExpected(proposals, buf, expecteds, previews);

		assertEqualStringsIgnoreOrder(previews, expecteds);
	}
	
	public void testJoinAndIfStatements1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0)) {\n");
		buf.append("            if (c) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (a");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0) && c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testJoinAndIfStatements2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0))\n");
		buf.append("            if (c) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (a");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0) && c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testJoinAndIfStatements3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0)) {\n");
		buf.append("            if (c) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (c");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0) && c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testJoinAndIfStatements4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0))\n");
		buf.append("            if (c) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (c");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a && (b == 0) && c) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testJoinAndIfStatementsBug335173() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a, int x) {\n");
		buf.append("        if (a instanceof String) {\n");
		buf.append("            if (x > 2) {\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (a");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a, int x) {\n");
		buf.append("        if (a instanceof String && x > 2) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testJoinOrIfStatements1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a)\n");
		buf.append("            return;\n");
		buf.append("        if (b == 5)\n");
		buf.append("            return;\n");
		buf.append("        b= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset1= buf.toString().indexOf("if (a");
		int offset2= buf.toString().lastIndexOf("b= 9;");
		AssistContext context= getCorrectionContext(cu, offset1, offset2 - offset1);
		List proposals= collectAssists(context, false);

		for (Iterator I= proposals.iterator(); I.hasNext();) {
			Object o= I.next();
			if (!(o instanceof CUCorrectionProposal))
				I.remove();
		}

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a || b == 5)\n");
		buf.append("            return;\n");
		buf.append("        b= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testJoinOrIfStatementsBug335173() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a, int x) {\n");
		buf.append("        if (a instanceof String)\n");
		buf.append("            return;\n");
		buf.append("        if (x > 2)\n");
		buf.append("            return;\n");
		buf.append("        x= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset1= buf.toString().indexOf("if (a");
		int offset2= buf.toString().lastIndexOf("x= 9;");
		AssistContext context= getCorrectionContext(cu, offset1, offset2 - offset1);
		List proposals= collectAssists(context, false);

		for (Iterator I= proposals.iterator(); I.hasNext();) {
			Object o= I.next();
			if (!(o instanceof CUCorrectionProposal))
				I.remove();
		}

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a, int x) {\n");
		buf.append("        if (a instanceof String || x > 2)\n");
		buf.append("            return;\n");
		buf.append("        x= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testSplitOrCondition1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a || b == 5)\n");
		buf.append("            return;\n");
		buf.append("        b= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("||");
		AssistContext context= getCorrectionContext(cu, offset, 0);

		List proposals= collectAssists(context, false);
		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a)\n");
		buf.append("            return;\n");
		buf.append("        else if (b == 5)\n");
		buf.append("            return;\n");
		buf.append("        b= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testSplitOrCondition2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a || b == 5)\n");
		buf.append("            return;\n");
		buf.append("        else {\n");
		buf.append("            b= 8;\n");
		buf.append("        }\n");
		buf.append("        b= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("||");
		AssistContext context= getCorrectionContext(cu, offset, 0);

		List proposals= collectAssists(context, false);
		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, boolean c) {\n");
		buf.append("        if (a)\n");
		buf.append("            return;\n");
		buf.append("        else if (b == 5)\n");
		buf.append("            return;\n");
		buf.append("        else {\n");
		buf.append("            b= 8;\n");
		buf.append("        }\n");
		buf.append("        b= 9;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testIfReturnIntoIfElseAtEndOfVoidMethod1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a) {\n");
		buf.append("            b= 9;\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("        b= 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a) {\n");
		buf.append("            b= 9;\n");
		buf.append("        } else {\n");
		buf.append("            b= 0;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfContinueIntoIfThenInLoops1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, ArrayList list) {\n");
		buf.append("        for (Iterator I = list.iterator(); I.hasNext();) {\n");
		buf.append("            if (a) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, ArrayList list) {\n");
		buf.append("        for (Iterator I = list.iterator(); I.hasNext();) {\n");
		buf.append("            if (!a)\n");
		buf.append("                continue;\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfIntoContinueInLoops1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, ArrayList list) {\n");
		buf.append("        for (Iterator I = list.iterator(); I.hasNext();) {\n");
		buf.append("            if (!a)\n");
		buf.append("                continue;\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.*;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, ArrayList list) {\n");
		buf.append("        for (Iterator I = list.iterator(); I.hasNext();) {\n");
		buf.append("            if (a) {\n");
		buf.append("                b= 9;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testRemoveExtraParentheses1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, Object o) {\n");
		buf.append("        if (a && (b == 0) && (o instanceof Integer) && (a || b)) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset1= buf.toString().indexOf("if (");
		int offset2= buf.toString().indexOf(") {", offset1);
		AssistContext context= getCorrectionContext(cu, offset1, offset2 - offset1);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, Object o) {\n");
		buf.append("        if (a && b == 0 && o instanceof Integer && (a || b)) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testRemoveExtraParentheses2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return (9+ 8);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		String str= "(9+ 8)";
		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf(str), 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo() {\n");
		buf.append("        return 9+ 8;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testAddParanoidalParentheses1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, int c, Object o) {\n");
		buf.append("        if (a && b == 0 && b + c > 3 && o instanceof Integer) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset1= buf.toString().indexOf("if (");
		int offset2= buf.toString().indexOf(") {", offset1);
		AssistContext context= getCorrectionContext(cu, offset1, offset2 - offset1);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b, int c, Object o) {\n");
		buf.append("        if (a && (b == 0) && ((b + c) > 3) && (o instanceof Integer)) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAddParenthesesForExpression1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object obj) {\n");
		buf.append("        if (obj instanceof String) {\n");
		buf.append("            String string = (String) obj;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("(String) obj"), 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object obj) {\n");
		buf.append("        if (obj instanceof String) {\n");
		buf.append("            String string = ((String) obj);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAddParenthesesForExpression2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object obj) {\n");
		buf.append("        if (obj instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("instanceof"), 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object obj) {\n");
		buf.append("        if ((obj instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAddParenthesesForExpression3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if (a + b == 0 && b + c > 3) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("=="), 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if ((a + b == 0) && b + c > 3) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAddParenthesesForExpression4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if (a + b == 0 && b + c > 3) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("+"), 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if ((a + b) == 0 && b + c > 3) {\n");
		buf.append("            b= 9;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAddParenthesesForExpression5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        int d = a > 10 ? b : c;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("?"), 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        int d = (a > 10 ? b : c);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAddParenthesesForExpression6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if (a > 3 && b > 5) {\n");
		buf.append("            a= 3;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("a > 3"), "a > 3".length());
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);
		assertNumberOfProposals(proposals, 7);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if ((a > 3) && b > 5) {\n");
		buf.append("            a= 3;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAddParenthesesForExpression7() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=338675
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if (a > 3 && b > 5) {\n");
		buf.append("            a= 3;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("a >"), "a >".length());
		List proposals= collectAssists(context, false);

		assertProposalDoesNotExist(proposals, "Put '>' expression in parentheses");
		
	}

	public void testAddParenthesesForExpression8() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=338675
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b, int c) {\n");
		buf.append("        if (a > 3 && b > 5) {\n");
		buf.append("            a= 3;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		AssistContext context= getCorrectionContext(cu, buf.toString().indexOf("a >"), 1);
		List proposals= collectAssists(context, false);

		assertProposalDoesNotExist(proposals, "Put '>' expression in parentheses");
		
	}

	public void testInverseIfCondition1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (a && (b == 0)) {\n");
		buf.append("            return;\n");
		buf.append("        } else {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, int b) {\n");
		buf.append("        if (!a || (b != 0)) {\n");
		buf.append("        } else {\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfCondition2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b, boolean c) {\n");
		buf.append("        if (a || b && c) {\n");
		buf.append("            return;\n");
		buf.append("        } else {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b, boolean c) {\n");
		buf.append("        if (!a && (!b || !c)) {\n");
		buf.append("        } else {\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfCondition3() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=75109
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a)\n");
		buf.append("            if (b) //inverse\n");
		buf.append("                return 1;\n");
		buf.append("            else\n");
		buf.append("                return 2;\n");
		buf.append("        return 17;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (b");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a)\n");
		buf.append("            if (!b)\n");
		buf.append("                return 2;\n");
		buf.append("            else\n");
		buf.append("                return 1;\n");
		buf.append("        return 17;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfCondition4() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=74580
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b, boolean c) {\n");
		buf.append("        if (a) {\n");
		buf.append("            one();\n");
		buf.append("        } else if (b) {\n");
		buf.append("            two();\n");
		buf.append("        } else {\n");
		buf.append("            three();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (a");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b, boolean c) {\n");
		buf.append("        if (!a) {\n");
		buf.append("            if (b) {\n");
		buf.append("                two();\n");
		buf.append("            } else {\n");
		buf.append("                three();\n");
		buf.append("            }\n");
		buf.append("        } else {\n");
		buf.append("            one();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfCondition5() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=74580
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        if (i == 1)\n");
		buf.append("            one();\n");
		buf.append("        else if (i == 2)\n");
		buf.append("            two();\n");
		buf.append("        else\n");
		buf.append("            three();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (i == 1");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i) {\n");
		buf.append("        if (i != 1) {\n");
		buf.append("            if (i == 2)\n");
		buf.append("                two();\n");
		buf.append("            else\n");
		buf.append("                three();\n");
		buf.append("        } else\n");
		buf.append("            one();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseIfCondition_bug119251() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=119251
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean a() { return false; }\n");
		buf.append("    private void foo(int i) {}\n");
		buf.append("    public void b() {\n");
		buf.append("        if (!a() && !a() && !a() && !a())\n");
		buf.append("            foo(1);\n");
		buf.append("        else\n");
		buf.append("            foo(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean a() { return false; }\n");
		buf.append("    private void foo(int i) {}\n");
		buf.append("    public void b() {\n");
		buf.append("        if (a() || a() || a() || a())\n");
		buf.append("            foo(2);\n");
		buf.append("        else\n");
		buf.append("            foo(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseIfCondition6() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=119251
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean a() { return false; }\n");
		buf.append("    private void foo(int i) {}\n");
		buf.append("    public void b() {\n");
		buf.append("        if (!a() && !a() || !a() && !a())\n");
		buf.append("            foo(1);\n");
		buf.append("        else\n");
		buf.append("            foo(2);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    private boolean a() { return false; }\n");
		buf.append("    private void foo(int i) {}\n");
		buf.append("    public void b() {\n");
		buf.append("        if ((a() || a()) && (a() || a()))\n");
		buf.append("            foo(2);\n");
		buf.append("        else\n");
		buf.append("            foo(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseIfConditionUnboxing() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=297645
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Boolean b) {\n");
		buf.append("        if (b) {\n");
		buf.append("            System.out.println(\"######\");\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(\"-\");\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
		
		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);
		
		assertCorrectLabels(proposals);
		
		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Boolean b) {\n");
		buf.append("        if (!b) {\n");
		buf.append("            System.out.println(\"-\");\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(\"######\");\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
		
		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseIfConditionEquals() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a == (b && a))\n");
		buf.append("            return 1;\n");
		buf.append("        else\n");
		buf.append("            return 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);
	
		int offset= buf.toString().indexOf("if (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);
	
		assertCorrectLabels(proposals);
	
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a != (b && a))\n");
		buf.append("            return 2;\n");
		buf.append("        else\n");
		buf.append("            return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();
	
		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseIfCondition_bug117960() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=117960
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a || b ? a : b) {\n");
		buf.append("            System.out.println();\n");
		buf.append("        } else {\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a || b ? !a : !b) {\n");
		buf.append("            return;\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testInverseConditionalStatement1() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=74746
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(boolean a) {\n");
		buf.append("        return a ? 4 : 5;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(boolean a) {\n");
		buf.append("        return !a ? 5 : 4;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseConditionalStatement2() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=74746
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a) {\n");
		buf.append("        return a + 6 == 9 ? 4 : 5;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a) {\n");
		buf.append("        return a + 6 != 9 ? 5 : 4;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInnerAndOuterIfConditions1() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=74746
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (a == 8) {\n");
		buf.append("            if (b instanceof String) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (a");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            if (a == 8) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInnerAndOuterIfConditions2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (a == 8)\n");
		buf.append("            if (b instanceof String) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (a");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String)\n");
		buf.append("            if (a == 8) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInnerAndOuterIfConditions3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (a == 8) {\n");
		buf.append("            if (b instanceof String) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (b");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            if (a == 8) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInnerAndOuterIfConditions4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (a == 8)\n");
		buf.append("            if (b instanceof String) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if (b");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public int foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String)\n");
		buf.append("            if (a == 8) {\n");
		buf.append("                return 0;\n");
		buf.append("            }\n");
		buf.append("        return 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperands1() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=74746
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, Object b) {\n");
		buf.append("        return a == b.hashCode();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("==");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, Object b) {\n");
		buf.append("        return b.hashCode() == a;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testExchangeOperands2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (0 == (a & b));\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("==");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return ((a & b) == 0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperands3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int n = (2 + 3) * (4 + 5);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("*");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int n = (4 + 5) * (2 + 3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperands4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (a < b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("<");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (b > a);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperands5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (a <= b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("<=");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (b >= a);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperands6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (a > b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf(">");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (b < a);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperands7() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (a >= b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf(">=");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (b <= a);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperandsBug332019_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return b != 0 != (a == b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!= (");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (a == b) != (b != 0);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperandsBug332019_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return b > 0 != (a == b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!=");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return (a == b) != b > 0;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperandsBug332019_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return b == 0 == true == false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("== false");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return false == (b == 0 == true);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testExchangeOperandsBug332019_4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return b + 1 != a - 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!=");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(int a, int b) {\n");
		buf.append("        return a - 1 != b + 1;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCast1() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=75066
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAssignAndCast2() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=75066
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (b instanceof String)\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAssignAndCastBug_104021() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = \"\";\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string2 = (String) b;\n");
		buf.append("            String string = \"\";\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAssignAndCastBug129336_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (!(b instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (!(b instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("        String string = (String) b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAssignAndCast129336_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (!(b instanceof String))\n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (!(b instanceof String))\n");
		buf.append("            return;\n");
		buf.append("        String string = (String) b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAssignAndCastBug129336_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (!(b instanceof String)) {\n");
		buf.append("        } else {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (!(b instanceof String)) {\n");
		buf.append("        } else {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testAssignAndCastBug331195_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("}") - 1;
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (b instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("}") - 1;
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("b instanceof");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("String)");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String && a > 10) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("}") - 1;
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String && a > 10) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            int x=10;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("int x") - 1;
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("            int x=10;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_7() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) \n");
		buf.append("            return;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("return") - 1;
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        if (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("            return;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testAssignAndCastBug331195_8() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (b instanceof String) \n");
		buf.append("            System.out.println(b);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("System") - 1;
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, Object b) {\n");
		buf.append("        while (b instanceof String) {\n");
		buf.append("            String string = (String) b;\n");
		buf.append("            System.out.println(b);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testReplaceReturnConditionWithIf1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        return (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        if (b == null)\n");
		buf.append("            return null;\n");
		buf.append("        else\n");
		buf.append("            return b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testReplaceReturnConditionWithIf2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        return (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("return");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        if (b == null)\n");
		buf.append("            return null;\n");
		buf.append("        else\n");
		buf.append("            return b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceReturnConditionWithIf3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        return (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int startOffset= buf.toString().indexOf("return");
		int endOffset= buf.toString().indexOf("    }");
		AssistContext context= getCorrectionContext(cu, startOffset, endOffset - startOffset - 1);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        if (b == null)\n");
		buf.append("            return null;\n");
		buf.append("        else\n");
		buf.append("            return b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceReturnConditionWithIf4() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=112443
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Collections;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    List<String> foo(List<String> list) {\n");
		buf.append("        return list != null ? list : Collections.emptyList();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("import java.util.Collections;\n");
		buf.append("import java.util.List;\n");
		buf.append("public class E {\n");
		buf.append("    List<String> foo(List<String> list) {\n");
		buf.append("        if (list != null)\n");
		buf.append("            return list;\n");
		buf.append("        else\n");
		buf.append("            return Collections.emptyList();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceAssignConditionWithIf1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testReplaceAssignConditionWithIf2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testReplaceAssignConditionWithIf3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        int i = 42;\n");
		buf.append("        i += ( b ) ? 1 : 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("?");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        int i = 42;\n");
		buf.append("        if (b)\n");
		buf.append("            i += 1;\n");
		buf.append("        else\n");
		buf.append("            i += 2;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testReplaceAssignConditionWithIf4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("res");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceAssignConditionWithIf5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("Object res");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceAssignConditionWithIf6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int startOffset= buf.toString().indexOf("Object res");
		int endOffset= buf.toString().indexOf("    }");
		AssistContext context= getCorrectionContext(cu, startOffset, endOffset - startOffset - 1);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceAssignConditionWithIf7() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("res=");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceAssignConditionWithIf8() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        res= (b == null) ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int startOffset= buf.toString().indexOf("res=");
		int endOffset= buf.toString().indexOf("    }");
		AssistContext context= getCorrectionContext(cu, startOffset, endOffset - startOffset - 1);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null)\n");
		buf.append("            res = null;\n");
		buf.append("        else\n");
		buf.append("            res = b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });
	}

	public void testReplaceReturnIfWithCondition() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        if (b == null) {\n");
		buf.append("            return null;\n");
		buf.append("        } else {\n");
		buf.append("            return b.toString();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public String foo(Object b) {\n");
		buf.append("        return b == null ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testReplaceReturnIfWithCondition2() throws Exception {
		try {
			JavaProjectHelper.set14CompilerOptions(fJProject1);

			IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
			StringBuffer buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class E {\n");
			buf.append("    public Number foo(Integer integer) {\n");
			buf.append("        if (integer != null) {\n");
			buf.append("            return integer;\n");
			buf.append("        } else {\n");
			buf.append("            return new Double(Double.MAX_VALUE);\n");
			buf.append("        }\n");
			buf.append("    }\n");
			buf.append("}\n");

			ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

			int offset= buf.toString().indexOf("if");
			AssistContext context= getCorrectionContext(cu, offset, 0);
			assertNoErrors(context);
			List proposals= collectAssists(context, false);

			assertCorrectLabels(proposals);

			buf= new StringBuffer();
			buf.append("package test1;\n");
			buf.append("public class E {\n");
			buf.append("    public Number foo(Integer integer) {\n");
			buf.append("        return integer != null ? integer : (Number) new Double(Double.MAX_VALUE);\n");
			buf.append("    }\n");
			buf.append("}\n");
			String expected1= buf.toString();

			assertExpectedExistInProposals(proposals, new String[] {expected1});
		} finally {
			JavaProjectHelper.set15CompilerOptions(fJProject1);
		}
	}



	public void testReplaceAssignIfWithCondition1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        if (b == null) {\n");
		buf.append("            res = null;\n");
		buf.append("        } else {\n");
		buf.append("            res = b.toString();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object b) {\n");
		buf.append("        Object res;\n");
		buf.append("        res = b == null ? null : b.toString();\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testReplaceAssignIfWithCondition2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        int res= 0;\n");
		buf.append("        if (b) {\n");
		buf.append("            res -= 2;\n");
		buf.append("        } else {\n");
		buf.append("            res -= 3;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("if");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        int res= 0;\n");
		buf.append("        res -= b ? 2 : 3;\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}



	public void testInverseVariable1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b) {\n");
		buf.append("        boolean var= false;\n");
		buf.append("        boolean d= var && b;\n");
		buf.append("        return d;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("var");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public boolean foo(boolean b) {\n");
		buf.append("        boolean notVar= true;\n");
		buf.append("        boolean d= !notVar && b;\n");
		buf.append("        return d;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseVariable2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean var= b && !b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("var");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean notVar= !b || b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseVariable2b() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean var= b & !b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("var");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean notVar= !b | b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testInverseVariable3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean var= true;\n");
		buf.append("        b= var && !var;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("var");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean notVar= false;\n");
		buf.append("        b= !notVar && notVar;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}


	public void testInverseVariable4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean var= false;\n");
		buf.append("        var |= b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("var");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        boolean notVar= true;\n");
		buf.append("        notVar &= !b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testInverseVariableBug117960() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        boolean var= a || b ? a : b;\n");
		buf.append("        var |= b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("var");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        boolean notVar= a || b ? !a : !b;\n");
		buf.append("        notVar &= !b;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInverseCondition1() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=334876
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a, Object b) {\n");
		buf.append("        if (a == null ^ b == null) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("a ==");
		int length= "a == null ^ b == null".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a, Object b) {\n");
		buf.append("        if (!(a == null ^ b == null)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInverseCondition2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        if (!(a instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!");
		int length= "!(a instanceof String)".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        if (a instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInverseCondition3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        while (!(a instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!");
		int length= "!(a instanceof String)".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        while (a instanceof String) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInverseCondition4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        for (int i = 0; !(a instanceof String); i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!");
		int length= "!(a instanceof String)".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        for (int i = 0; a instanceof String; i++) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInverseCondition5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        do {\n");
		buf.append("        } while (!(a instanceof String));\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!");
		int length= "!(a instanceof String)".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        do {\n");
		buf.append("        } while (a instanceof String);\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testInverseCondition6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        assert !(a instanceof String);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!");
		int length= "!(a instanceof String)".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        assert a instanceof String;\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testPushNegationDown1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k) {\n");
		buf.append("        boolean b= (i > 1) || !(j < 2 || k < 3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!(");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k) {\n");
		buf.append("        boolean b= (i > 1) || j >= 2 && k >= 3;\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testPushNegationDown2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k) {\n");
		buf.append("        boolean b= (i > 1) && !(j < 2 && k < 3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!(");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k) {\n");
		buf.append("        boolean b= (i > 1) && (j >= 2 || k >= 3);\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testPushNegationDown3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k) {\n");
		buf.append("        boolean b= (i > 1) || !(j < 2 || k < 3);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("(j < 2");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k) {\n");
		buf.append("        boolean b= (i > 1) || j >= 2 && k >= 3;\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testPushNegationDownBug335778_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        if (!(b)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!(");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		assertProposalDoesNotExist(proposals, "Push negation down");
	}

	public void testPushNegationDownBug335778_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        if (!(a instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!(");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		assertProposalDoesNotExist(proposals, "Push negation down");
	}

	public void testPushNegationDownBug117960() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (!(a || b ? !a : !b)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!(");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a || b ? a : b) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testPullNegationUp() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k, int m, int n) {\n");
		buf.append("        boolean b = i > 1 || j >= 2 && k >= 3 || m > 4 || n > 5;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset1= buf.toString().indexOf("j >= 2");
		int offset2= buf.toString().indexOf(" || m > 4");
		AssistContext context= getCorrectionContext(cu, offset1, offset2 - offset1);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int i, int j, int k, int m, int n) {\n");
		buf.append("        boolean b = i > 1 || !(j < 2 || k < 3) || m > 4 || n > 5;\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testPullNegationUpBug335778_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean b) {\n");
		buf.append("        if (!b) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!b");
		int length= "!b".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		assertProposalDoesNotExist(proposals, "Pull negation up");
	}

	public void testPullNegationUpBug335778_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(Object a) {\n");
		buf.append("        if (!(a instanceof String)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("!(");
		int length= "!(a instanceof String)".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		assertProposalDoesNotExist(proposals, "Pull negation up");
	}

	public void testPullNegationUpBug117960() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (a || b ? a : b) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("a || b");
		int length= "a || b ? a : b".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(boolean a, boolean b) {\n");
		buf.append("        if (!(a || b ? !a : !b)) {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testJoinIfListInIfElseIf() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b) {\n");
		buf.append("        if (a == 1)\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        if (a == 2)\n");
		buf.append("            if (b > 0)\n");
		buf.append("                System.out.println(2);\n");
		buf.append("        if (a == 3)\n");
		buf.append("            if (b > 0)\n");
		buf.append("                System.out.println(3);\n");
		buf.append("            else\n");
		buf.append("                System.out.println(-3);\n");
		buf.append("        if (a == 4)\n");
		buf.append("            System.out.println(4);\n");
		buf.append("        int stop;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset1= buf.toString().indexOf("if (a == 1)");
		int offset2= buf.toString().indexOf("int stop;");
		AssistContext context= getCorrectionContext(cu, offset1, offset2 - offset1);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a, int b) {\n");
		buf.append("        if (a == 1)\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        else if (a == 2) {\n");
		buf.append("            if (b > 0)\n");
		buf.append("                System.out.println(2);\n");
		buf.append("        } else if (a == 3)\n");
		buf.append("            if (b > 0)\n");
		buf.append("                System.out.println(3);\n");
		buf.append("            else\n");
		buf.append("                System.out.println(-3); else if (a == 4)\n");
		buf.append("                System.out.println(4);\n");
		buf.append("        int stop;\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}


	public void testConvertSwitchToIf() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a) {\n");
		buf.append("        switch (a) {\n");
		buf.append("            case 1:\n");
		buf.append("                {\n");
		buf.append("                    System.out.println(1);\n");
		buf.append("                    break;\n");
		buf.append("                }\n");
		buf.append("            case 2:\n");
		buf.append("            case 3:\n");
		buf.append("                System.out.println(2);\n");
		buf.append("                break;\n");
		buf.append("            case 4:\n");
		buf.append("                System.out.println(4);\n");
		buf.append("                return;\n");
		buf.append("            default:\n");
		buf.append("                System.out.println(-1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("switch");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int a) {\n");
		buf.append("        if (a == 1) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        } else if (a == 2 || a == 3) {\n");
		buf.append("            System.out.println(2);\n");
		buf.append("        } else if (a == 4) {\n");
		buf.append("            System.out.println(4);\n");
		buf.append("            return;\n");
		buf.append("        } else {\n");
		buf.append("            System.out.println(-1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});

	}

	public void testConvertSwitchToIf2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("pack", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public enum TimeUnit {\n");
		buf.append("        SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS\n");
		buf.append("    }\n");
		buf.append("    public static int getPower(TimeUnit unit) {\n");
		buf.append("        switch (unit) {\n");
		buf.append("        case SECONDS:\n");
		buf.append("                return 0;\n");
		buf.append("        case MILLISECONDS:\n");
		buf.append("                return -3;\n");
		buf.append("        case MICROSECONDS:\n");
		buf.append("                return -6;\n");
		buf.append("        case NANOSECONDS:\n");
		buf.append("                return -9;\n");
		buf.append("        default:\n");
		buf.append("                throw new InternalError();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("switch");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    public enum TimeUnit {\n");
		buf.append("        SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS\n");
		buf.append("    }\n");
		buf.append("    public static int getPower(TimeUnit unit) {\n");
		buf.append("        if (unit == TimeUnit.SECONDS) {\n");
		buf.append("            return 0;\n");
		buf.append("        } else if (unit == TimeUnit.MILLISECONDS) {\n");
		buf.append("            return -3;\n");
		buf.append("        } else if (unit == TimeUnit.MICROSECONDS) {\n");
		buf.append("            return -6;\n");
		buf.append("        } else if (unit == TimeUnit.NANOSECONDS) {\n");
		buf.append("            return -9;\n");
		buf.append("        } else {\n");
		buf.append("            throw new InternalError();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testConvertSwitchToIf3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("pack", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    final static int SECONDS=1, MILLISECONDS=2, MICROSECONDS=4,NANOSECONDS=8;\n");
		buf.append("    public static int getPower(int unit) {\n");
		buf.append("        switch (unit) {\n");
		buf.append("        case SECONDS:\n");
		buf.append("                return 0;\n");
		buf.append("        case MILLISECONDS:\n");
		buf.append("                return -3;\n");
		buf.append("        case MICROSECONDS:\n");
		buf.append("                return -6;\n");
		buf.append("        case NANOSECONDS:\n");
		buf.append("                return -9;\n");
		buf.append("        default:\n");
		buf.append("                throw new InternalError();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("A.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("switch");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package pack;\n");
		buf.append("\n");
		buf.append("public class A {\n");
		buf.append("    final static int SECONDS=1, MILLISECONDS=2, MICROSECONDS=4,NANOSECONDS=8;\n");
		buf.append("    public static int getPower(int unit) {\n");
		buf.append("        if (unit == SECONDS) {\n");
		buf.append("            return 0;\n");
		buf.append("        } else if (unit == MILLISECONDS) {\n");
		buf.append("            return -3;\n");
		buf.append("        } else if (unit == MICROSECONDS) {\n");
		buf.append("            return -6;\n");
		buf.append("        } else if (unit == NANOSECONDS) {\n");
		buf.append("            return -9;\n");
		buf.append("        } else {\n");
		buf.append("            throw new InternalError();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] {expected1});
	}

	public void testConvertSwitchToIfBug252104_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foobar() {\n");
		buf.append("        switch (getFoo() ? getBar() : getBar()) {\n");
		buf.append("        case 1:\n");
		buf.append("            System.out.println();\n");
		buf.append("            break;\n");
		buf.append("        case 2:\n");
		buf.append("            System.out.println();\n");
		buf.append("            break;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    private int getBar() {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("    private boolean getFoo() {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("switch");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foobar() {\n");
		buf.append("        if ((getFoo() ? getBar() : getBar()) == 1) {\n");
		buf.append("            System.out.println();\n");
		buf.append("        } else if ((getFoo() ? getBar() : getBar()) == 2) {\n");
		buf.append("            System.out.println();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("    private int getBar() {\n");
		buf.append("        return 0;\n");
		buf.append("    }\n");
		buf.append("    private boolean getFoo() {\n");
		buf.append("        return false;\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testConvertSwitchToIfBug252104_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int x, int y) {\n");
		buf.append("        switch (x + y) {\n");
		buf.append("        case 1:\n");
		buf.append("            System.out.println();\n");
		buf.append("            break;\n");
		buf.append("        case 2:\n");
		buf.append("            System.out.println();\n");
		buf.append("            break;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("switch");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		assertNoErrors(context);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo(int x, int y) {\n");
		buf.append("        if (x + y == 1) {\n");
		buf.append("            System.out.println();\n");
		buf.append("        } else if (x + y == 2) {\n");
		buf.append("            System.out.println();\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");

		String expected1= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected1 });

	}

	public void testSurroundWithTemplate01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        System.out.println(1);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);

		String selection= "System.out.println(1);";
		int offset= buf.toString().indexOf(selection);

		AssistContext context= getCorrectionContext(cu, offset, selection.length());
		assertNoErrors(context);
		List proposals= Arrays.asList(new QuickTemplateProcessor().getAssists(context, null));

		assertCorrectLabels(proposals);
		assertNumberOfProposals(proposals, 7);

		String[] expected= new String[7];

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        do {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        } while (condition);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[0]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        for (int i = 0; i < array.length; i++) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[1]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        if (condition) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[2]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                System.out.println(1);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[3]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        synchronized (mutex) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[4]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        try {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            // TODO: handle exception\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[5]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        while (condition) {\n");
		buf.append("            System.out.println(1);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[6]= buf.toString();

		assertExpectedExistInProposals(proposals, expected);
	}

	public void testSurroundWithTemplate02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);

		String selection= "System.out.println(i);";
		int offset= buf.toString().indexOf(selection);

		AssistContext context= getCorrectionContext(cu, offset, selection.length());
		assertNoErrors(context);
		List proposals= Arrays.asList(new QuickTemplateProcessor().getAssists(context, null));

		assertCorrectLabels(proposals);
		assertNumberOfProposals(proposals, 7);

		String[] expected= new String[7];

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        do {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        } while (condition);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[0]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        for (int j = 0; j < array.length; j++) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[1]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        if (condition) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[2]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        final int i= 10;\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                System.out.println(i);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[3]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        synchronized (mutex) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[4]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        try {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            // TODO: handle exception\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[5]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        while (condition) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[6]= buf.toString();

		assertExpectedExistInProposals(proposals, expected);
	}

	public void testSurroundWithTemplate03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        System.out.println(i);\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);

		String selection= "int i= 10;\n        System.out.println(i);";
		int offset= buf.toString().indexOf(selection);

		AssistContext context= getCorrectionContext(cu, offset, selection.length());
		assertNoErrors(context);
		List proposals= Arrays.asList(new QuickTemplateProcessor().getAssists(context, null));

		assertCorrectLabels(proposals);
		assertNumberOfProposals(proposals, 7);

		String[] expected= new String[7];

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        do {\n");
		buf.append("            i = 10;\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        } while (condition);\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[0]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        for (int j = 0; j < array.length; j++) {\n");
		buf.append("            i = 10;\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[1]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        if (condition) {\n");
		buf.append("            i = 10;\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[2]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                i = 10;\n");
		buf.append("                System.out.println(i);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[3]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        synchronized (mutex) {\n");
		buf.append("            i = 10;\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[4]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        try {\n");
		buf.append("            i = 10;\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            // TODO: handle exception\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[5]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i;\n");
		buf.append("        while (condition) {\n");
		buf.append("            i = 10;\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[6]= buf.toString();

		assertExpectedExistInProposals(proposals, expected);
	}

	public void testSurroundWithTemplate04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        System.out.println(i);\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", buf.toString(), false, null);

		String selection= "System.out.println(i);";
		int offset= buf.toString().indexOf(selection);

		AssistContext context= getCorrectionContext(cu, offset, selection.length());
		assertNoErrors(context);
		List proposals= Arrays.asList(new QuickTemplateProcessor().getAssists(context, null));

		assertCorrectLabels(proposals);
		assertNumberOfProposals(proposals, 7);

		String[] expected= new String[7];

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        do {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        } while (condition);\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[0]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        for (int j = 0; j < array.length; j++) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[1]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        if (condition) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[2]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        final int i= 10;\n");
		buf.append("        new Runnable() {\n");
		buf.append("            public void run() {\n");
		buf.append("                System.out.println(i);\n");
		buf.append("            }\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[3]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        synchronized (mutex) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[4]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        try {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        } catch (Exception e) {\n");
		buf.append("            // TODO: handle exception\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[5]= buf.toString();

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E1 {\n");
		buf.append("    public void foo() {\n");
		buf.append("        int i= 10;\n");
		buf.append("        while (condition) {\n");
		buf.append("            System.out.println(i);\n");
		buf.append("        }\n");
		buf.append("        System.out.println(i);\n");
		buf.append("    }\n");
		buf.append("}\n");
		expected[6]= buf.toString();

		assertExpectedExistInProposals(proposals, expected);
	}

	public void testPickOutStringProposals1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String string = \"Hello World\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("llo");
		int length= "llo".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String string = \"He\" + \"llo\" + \" World\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected });

	}

	public void testPickOutStringProposals2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String string = \"Hello World\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("Hel");
		int length= "Hel".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertCorrectLabels(proposals);

		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String string = \"Hel\" + \"lo World\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		String expected= buf.toString();

		assertExpectedExistInProposals(proposals, new String[] { expected });

	}

	public void testPickOutStringProposals3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String string = \"Hello World\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("World");
		AssistContext context= getCorrectionContext(cu, offset, 0);
		List proposals= collectAssists(context, false);

		assertProposalDoesNotExist(proposals, "Pick out selected part of String");

	}

	public void testPickOutStringProposals4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class E {\n");
		buf.append("    public void foo() {\n");
		buf.append("        String string = \"Hello World\";\n");
		buf.append("    }\n");
		buf.append("}\n");
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		int offset= buf.toString().indexOf("Hello");
		int length= "Hello World".length();
		AssistContext context= getCorrectionContext(cu, offset, length);
		List proposals= collectAssists(context, false);

		assertProposalDoesNotExist(proposals, "Pick out selected part of String");

	}
}
