package com.avast.android.butterknifezelezny;

import com.avast.android.butterknifezelezny.butterknife.ButterKnifeFactory;
import com.avast.android.butterknifezelezny.butterknife.IButterKnife;
import com.avast.android.butterknifezelezny.common.Definitions;
import com.avast.android.butterknifezelezny.common.Utils;
import com.avast.android.butterknifezelezny.model.Element;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.EverythingGlobalScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class InjectWriter extends WriteCommandAction.Simple {

    protected PsiFile mFile;
    protected Project mProject;
    protected PsiClass mClass;
    protected ArrayList<Element> mElements;
    protected PsiElementFactory mFactory;
    protected String mLayoutFileName;
    protected String mFieldNamePrefix;
    protected boolean mCreateHolder;

    public InjectWriter(PsiFile file, PsiClass clazz, String command, ArrayList<Element> elements, String layoutFileName, String fieldNamePrefix, boolean createHolder) {
        super(clazz.getProject(), command);

        mFile = file;
        mProject = clazz.getProject();
        mClass = clazz;
        mElements = elements;
        mFactory = JavaPsiFacade.getElementFactory(mProject);
        mLayoutFileName = layoutFileName;
        mFieldNamePrefix = fieldNamePrefix;
        mCreateHolder = createHolder;
    }

    @Override
    public void run() throws Throwable {
        final IButterKnife butterKnife = ButterKnifeFactory.findButterKnifeForPsiElement();
        if (butterKnife == null) {
            return;
        }

        if (mCreateHolder) {
            generateAdapter(butterKnife);
        } else {
            if (Utils.getInjectCount(mElements) > 0) {
                generateFields();
            }
            generateInjects(butterKnife);
            if (Utils.getClickCount(mElements) > 0) {
                generateClick();
            }
            Utils.showInfoNotification(mProject, String.valueOf(Utils.getInjectCount(mElements)) + " injections and " + String.valueOf(Utils.getClickCount(mElements)) + " onClick added to " + mFile.getName());
        }

        // reformat class
        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(mProject);
        styleManager.optimizeImports(mFile);
        styleManager.shortenClassReferences(mClass);
        new ReformatCodeProcessor(mProject, mClass.getContainingFile(), null, false).runWithoutProgress();
    }

    protected void generateClick() {
        StringBuilder method = new StringBuilder();
        method.append("@Override ");
        method.append("public void onClick(android.view.View view) {switch (view.getId()){");
        for (Element element : mElements) {
            if (element.isClick) {
                method.append("case " + element.getFullID() + ": break;");
            }
        }
        method.append("}}");

        mClass.add(mFactory.createMethodFromText(method.toString(), mClass));

    }


    /**
     * Create ViewHolder for adapters with injections
     */
    protected void generateAdapter(@NotNull IButterKnife butterKnife) {
        // view holder class
        StringBuilder holderBuilder = new StringBuilder();
        holderBuilder.append(Utils.getViewHolderClassName());
        holderBuilder.append("(android.view.View itemView) {");
        holderBuilder.append(" super(itemView);");

        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }
            holderBuilder.append(element.fieldName);
            holderBuilder.append(" = (");
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                holderBuilder.append(element.nameFull);
            } else if (Definitions.paths.containsKey(element.name)) { // listed class
                holderBuilder.append(Definitions.paths.get(element.name));
            } else { // android.widget
                holderBuilder.append("android.widget.");
                holderBuilder.append(element.name);
            }
            holderBuilder.append(")itemView.findViewById(");

            String rPrefix;
            if (element.isAndroidNS) {
                rPrefix = "android.R.id.";
            } else {
                rPrefix = "R.id.";
            }
            holderBuilder.append(rPrefix);
            holderBuilder.append(element.id);
            holderBuilder.append("); ");
        }

        holderBuilder.append("}");

        PsiClass viewHolder = mFactory.createClassFromText(holderBuilder.toString(), mClass);


        viewHolder.setName(Utils.getViewHolderClassName());

        // add injections into view holder
        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }
            StringBuilder injection = new StringBuilder();
            injection.append("private ");
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                injection.append(element.nameFull);
            } else if (Definitions.paths.containsKey(element.name)) { // listed class
                injection.append(Definitions.paths.get(element.name));
            } else { // android.widget
                injection.append("android.widget.");
                injection.append(element.name);
            }
            injection.append(" ");
            injection.append(element.fieldName);
            injection.append(";");

            viewHolder.add(mFactory.createFieldFromText(injection.toString(), mClass));
        }

        mClass.add(viewHolder);
        mClass.addBefore(mFactory.createKeyword("static", mClass), mClass.findInnerClassByName(Utils.getViewHolderClassName(), true));
    }

    /**
     * Create fields for injections inside main class
     */
    protected void generateFields() {
        // add injections into main class
        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }

            StringBuilder injection = new StringBuilder();
            injection.append("private ");
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                injection.append(element.nameFull);
            } else if (Definitions.paths.containsKey(element.name)) { // listed class
                injection.append(Definitions.paths.get(element.name));
            } else { // android.widget
                injection.append("android.widget.");
                injection.append(element.name);
            }
            injection.append(" ");
            injection.append(element.fieldName);
            injection.append(";");

            mClass.add(mFactory.createFieldFromText(injection.toString(), mClass));
        }
    }

    protected void generateFindViewMethod(boolean haveRootView) {
        StringBuilder method = new StringBuilder();
        if (haveRootView) {
            method.append("private void findView(android.view.View view) {");
        } else {
            method.append("private void findView() {");
        }
        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }
            method.append(element.fieldName);
            method.append(" = (");
            if (element.nameFull != null && element.nameFull.length() > 0) { // custom package+class
                method.append(element.nameFull);
            } else if (Definitions.paths.containsKey(element.name)) { // listed class
                method.append(Definitions.paths.get(element.name));
            } else { // android.widget
                method.append("android.widget.");
                method.append(element.name);
            }
            if (haveRootView) {
                method.append(")view.findViewById(");
            } else {

                method.append(")findViewById(");
            }
            String rPrefix;
            if (element.isAndroidNS) {
                rPrefix = "android.R.id.";
            } else {
                rPrefix = "R.id.";
            }
            method.append(rPrefix);
            method.append(element.id);
            method.append("); ");
        }
        method.append("}");

        mClass.add(mFactory.createMethodFromText(method.toString(), mClass));

    }

    protected void generateSetListener() {
        StringBuilder method = new StringBuilder();

        method.append("private void setListener() {");
        for (Element element : mElements) {
            if (!element.used) {
                continue;
            }
            method.append(element.fieldName);

            method.append(".setOnClickListener(this); ");

        }
        method.append("}");

        mClass.add(mFactory.createMethodFromText(method.toString(), mClass));

    }

    private boolean containsButterKnifeInjectLine(PsiMethod method, String line) {
        final PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        PsiStatement[] statements = body.getStatements();
        for (PsiStatement psiStatement : statements) {
            String statementAsString = psiStatement.getText();
            if (psiStatement instanceof PsiExpressionStatement && (statementAsString.contains(line))) {
                return true;
            }
        }
        return false;
    }

    protected void generateInjects(@NotNull IButterKnife butterKnife) {
        PsiClass activityClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.app.Activity", new EverythingGlobalScope(mProject));
        PsiClass fragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.app.Fragment", new EverythingGlobalScope(mProject));
        PsiClass supportFragmentClass = JavaPsiFacade.getInstance(mProject).findClass(
                "android.support.v4.app.Fragment", new EverythingGlobalScope(mProject));

        // Check for Activity class
        if (activityClass != null && mClass.isInheritor(activityClass, true)) {
            generateActivityBind(butterKnife);
            // Check for Fragment class
        } else if ((fragmentClass != null && mClass.isInheritor(fragmentClass, true)) || (supportFragmentClass != null && mClass.isInheritor(supportFragmentClass, true))) {
            generateFragmentBindAndUnbind(butterKnife);
        }
    }

    private void generateActivityBind(@NotNull IButterKnife butterKnife) {
        if (mClass.findMethodsByName("onCreate", false).length == 0) {
            // Add an empty stub of onCreate()
            StringBuilder method = new StringBuilder();
            method.append("@Override protected void onCreate(android.os.Bundle savedInstanceState) {\n");
            method.append("super.onCreate(savedInstanceState);\n");
            method.append("\t// TODO: add setContentView(...) invocation\n");
            method.append("findView();\n");
            for (Element element : mElements) {
                if (element.isClick) {
                    method.append("setListener();\n");
                    generateSetListener();
                    break;
                }
            }
            method.append("}");

            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
        } else {
            PsiMethod onCreate = mClass.findMethodsByName("onCreate", false)[0];
            if (!containsButterKnifeInjectLine(onCreate, butterKnife.getSimpleBindStatement())) {
                for (PsiStatement statement : onCreate.getBody().getStatements()) {
                    // Search for setContentView()
                    if (statement.getFirstChild() instanceof PsiMethodCallExpression) {
                        PsiReferenceExpression methodExpression
                                = ((PsiMethodCallExpression) statement.getFirstChild())
                                .getMethodExpression();
                        // Insert ButterKnife.inject()/ButterKnife.bind() after setContentView()
                        if (methodExpression.getText().equals("setContentView")) {
                            for (Element element : mElements) {
                                if (element.isClick) {
                                    onCreate.getBody().addAfter(mFactory.createStatementFromText("setListener();", mClass), statement);
                                    generateSetListener();
                                    break;
                                }
                            }
                            onCreate.getBody().addAfter(mFactory.createStatementFromText("findView();", mClass), statement);
                            break;
                        }
                    }
                }
            }
        }
        generateFindViewMethod(false);
    }

    private void generateFragmentBindAndUnbind(@NotNull IButterKnife butterKnife) {

        // onCreateView() doesn't exist, let's create it
        if (mClass.findMethodsByName("onCreateView", false).length == 0) {
            // Add an empty stub of onCreateView()
            StringBuilder method = new StringBuilder();
            method.append("@Override public View onCreateView(android.view.LayoutInflater inflater, android.view.ViewGroup container, android.os.Bundle savedInstanceState) {\n");
            method.append("\t// TODO: inflate a fragment view\n");
            method.append("android.view.View rootView = super.onCreateView(inflater, container, savedInstanceState);\n");
            method.append("findView(rootView);\n");
            for (Element element : mElements) {
                if (element.isClick) {
                    method.append("setListener();\n");
                    generateSetListener();
                    break;
                }
            }
            method.append("return rootView;\n");
            method.append("}");

            mClass.add(mFactory.createMethodFromText(method.toString(), mClass));
        } else {
            // onCreateView() exists, let's update it with an inject/bind statement
            PsiMethod onCreateView = mClass.findMethodsByName("onCreateView", false)[0];
            if (!containsButterKnifeInjectLine(onCreateView, butterKnife.getSimpleBindStatement())) {
                for (PsiStatement statement : onCreateView.getBody().getStatements()) {
                    if (statement instanceof PsiReturnStatement) {
                        String returnValue = ((PsiReturnStatement) statement).getReturnValue().getText();
                        // there's layout inflatiion
                        if (returnValue.contains("R.layout")) {
                            onCreateView.getBody().addBefore(mFactory.createStatementFromText("android.view.View view = " + returnValue + ";", mClass), statement);
                            StringBuilder bindText = new StringBuilder();

                            bindText.append("findView(view); ");
                            for (Element element : mElements) {
                                if (element.isClick) {
                                    onCreateView.getBody().addBefore(mFactory.createStatementFromText("setListener(); ", mClass), statement);
                                    generateSetListener();
                                    break;
                                }
                            }
                            PsiStatement bindStatement = mFactory.createStatementFromText(bindText.toString(), mClass);
                            onCreateView.getBody().addBefore(bindStatement, statement);
                            statement.replace(mFactory.createStatementFromText("return view;", mClass));

                        } else {
                            // Insert ButterKnife.inject()/ButterKnife.bind() before returning a view for a fragment
                            StringBuilder bindText = new StringBuilder();
                            bindText.append("findView(view); ");
                            for (Element element : mElements) {
                                if (element.isClick) {
                                    onCreateView.getBody().addBefore(mFactory.createStatementFromText("setListener(); ", mClass), statement);
                                    generateSetListener();
                                    break;
                                }
                            }
                            PsiStatement bindStatement = mFactory.createStatementFromText(bindText.toString(), mClass);
                            onCreateView.getBody().addBefore(bindStatement, statement);

                        }
                        break;
                    }
                }
            }
        }
        generateFindViewMethod(true);


    }


}