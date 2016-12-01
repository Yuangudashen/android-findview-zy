package com.avast.android.butterknifezelezny.butterknife;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for obtaining proper ButterKnife version.
 *
 * @author Tomáš Kypta
 * @since 1.3
 */
public class ButterKnifeFactory {

    private static IButterKnife butterKnife = new ButterKnife();

    private ButterKnifeFactory() {
        // no construction
    }

    /**
     * Find ButterKnife that is available for given {@link PsiElement} in the {@link Project}.
     * Note that it check if ButterKnife is available in the module.
     *
     * @return ButterKnife
     */
    @Nullable
    public static IButterKnife findButterKnifeForPsiElement() {
        return butterKnife;
    }


}
