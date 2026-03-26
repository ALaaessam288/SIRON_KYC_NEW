package com.bpm.core.PDFSIRON.Configs;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import com.bpm.core.PDFSIRON.PDFGenerator;

import java.beans.BeanDescriptor;
import java.beans.MethodDescriptor;
import java.beans.ParameterDescriptor;
import java.beans.SimpleBeanInfo;

public class PDFGeneratorBeanInfo extends SimpleBeanInfo {
    public PDFGeneratorBeanInfo() {
    }

    public MethodDescriptor[] getMethodDescriptors() {
        try {
            MethodDescriptor generatePDFAsBase64 = new MethodDescriptor(PDFGenerator.class.getMethod("generatePDFAsBase64", String.class, String.class), new ParameterDescriptor[]{this.createParam("fullName"), this.createParam("JsonContent")});
            return new MethodDescriptor[]{generatePDFAsBase64};
        } catch (NoSuchMethodException var2) {
            NoSuchMethodException e = var2;
            e.printStackTrace();
            return super.getMethodDescriptors();
        }
    }

    private ParameterDescriptor createParam(String name) {
        ParameterDescriptor pd = new ParameterDescriptor();
        pd.setName(name);
        return pd;
    }

    public BeanDescriptor getBeanDescriptor() {
        return new BeanDescriptor(PDFGenerator.class);
    }
}
