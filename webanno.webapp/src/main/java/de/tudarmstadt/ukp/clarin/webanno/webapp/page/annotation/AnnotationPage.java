/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectByAddr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.uima.UIMAException;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;

import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;
import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.project.ProjectUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.clarin.webanno.webapp.dialog.OpenModalWindowPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.home.page.ApplicationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.AnnotationLayersModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.ExportModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.FinishLink;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.annotation.component.GuidelineModalPanel;
import de.tudarmstadt.ukp.clarin.webanno.webapp.page.welcome.WelcomePage;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

/**
 * A wicket page for the Brat Annotation/Visualization page. Included components for pagination,
 * annotation layer configuration, and Exporting document
 *
 * @author Seid Muhie Yimam
 *
 */
public class AnnotationPage
    extends ApplicationPageBase
{
    private static final Log LOG = LogFactory.getLog(AnnotationPage.class);    
    
    private static final long serialVersionUID = 1378872465851908515L;
    @SpringBean(name = "jsonConverter")
    private MappingJacksonHttpMessageConverter jsonConverter;

    @SpringBean(name = "documentRepository")
    private RepositoryService repository;

    @SpringBean(name = "annotationService")
    private AnnotationService annotationService;

    private BratAnnotator annotator;

    private FinishImage finish;
    private int windowSize;

    private NumberTextField<Integer> gotoPageTextField;

    private int gotoPageAddress;

    // Open the dialog window on first load
    boolean firstLoad = true;

    private Label numberOfPages;
    private DocumentNamePanel documentNamePanel;

    private long currentDocumentId;
    private long currentprojectId;

    private int sentenceNumber = 1;
    private int totalNumberOfSentence;

    private boolean closeButtonClicked;
    public BratAnnotatorModel bratAnnotatorModel = new BratAnnotatorModel();

    public AnnotationPage()
    {
        annotator = new BratAnnotator("embedder1",
                new Model<BratAnnotatorModel>(bratAnnotatorModel))
        {

            private static final long serialVersionUID = 7279648231521710155L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget,
                    BratAnnotatorModel aBratAnnotatorModel)
            {
                bratAnnotatorModel = aBratAnnotatorModel;
                aTarget.add(numberOfPages);
            }
            
            @Override
            public void renderHead(IHeaderResponse aResponse)
            {
                super.renderHead(aResponse);
                
                // If the page is reloaded in the browser and a document was already open, we need 
                // to render it. We use the "later" commands here to avoid polluting the Javascript
                // header items with document data and because loading times are not that critical
                // on a reload.
                if (getModelObject().getProject() != null) {
                    aResponse.render(OnLoadHeaderItem.forScript(bratInitLaterCommand()));
                    aResponse.render(OnLoadHeaderItem.forScript(bratRenderLaterCommand()));
                }
            }
        };

        // This is an Annotation Operation, set model to ANNOTATION mode
        bratAnnotatorModel.setMode(Mode.ANNOTATION);
        add(annotator);

        add(documentNamePanel = (DocumentNamePanel) new DocumentNamePanel("documentNamePanel",
                new Model<BratAnnotatorModel>(bratAnnotatorModel)).setOutputMarkupId(true));

        add(numberOfPages = (Label) new Label("numberOfPages",
                new LoadableDetachableModel<String>()
                {

                    private static final long serialVersionUID = 1L;

                    @Override
                    protected String load()
                    {
                        if (bratAnnotatorModel.getDocument() != null) {
                            try {
                                JCas jCas = getCas(bratAnnotatorModel.getProject(),
                                        bratAnnotatorModel.getUser(),
                                        bratAnnotatorModel.getDocument());
                                totalNumberOfSentence = BratAjaxCasUtil.getNumberOfPages(jCas);

                                // If only one page, start displaying from sentence 1
                                if (totalNumberOfSentence == 1) {
                                    bratAnnotatorModel.setSentenceAddress(bratAnnotatorModel
                                            .getFirstSentenceAddress());
                                }
                                sentenceNumber = BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                                        bratAnnotatorModel.getSentenceAddress());
                                int firstSentenceNumber = sentenceNumber + 1;
                                int lastSentenceNumber;
                                if (firstSentenceNumber + bratAnnotatorModel.getWindowSize() - 1 < totalNumberOfSentence) {
                                    lastSentenceNumber = firstSentenceNumber
                                            + bratAnnotatorModel.getWindowSize() - 1;
                                }
                                else {
                                    lastSentenceNumber = totalNumberOfSentence;
                                }

                                return "showing " + firstSentenceNumber + "-" + lastSentenceNumber
                                        + " of " + totalNumberOfSentence + " sentences";
                            }
                            // No need to report error, already reported in getDocument below
                            catch (DataRetrievalFailureException ex) {
                                // error(ExceptionUtils.getRootCauseMessage(ex));
                                return "";
                            }
                            // No need to report error, already reported in getDocument below
                            catch (UIMAException e) {
                                // error(ExceptionUtils.getRootCauseMessage(e));
                                return "";
                            }
                            catch (IOException e) {
                                return "";
                            }
                            catch (ClassNotFoundException e) {
                                return "";
                            }

                        }
                        else {
                            return "";// no document yet selected
                        }

                    }
                }).setOutputMarkupId(true));

        final ModalWindow openDocumentsModal;
        add(openDocumentsModal = new ModalWindow("openDocumentsModal"));
        openDocumentsModal.setOutputMarkupId(true);

        openDocumentsModal.setInitialWidth(500);
        openDocumentsModal.setInitialHeight(300);
        openDocumentsModal.setResizable(true);
        openDocumentsModal.setWidthUnit("px");
        openDocumentsModal.setHeightUnit("px");
        openDocumentsModal.setTitle("Open document");
        openDocumentsModal.setCloseButtonCallback(new ModalWindow.CloseButtonCallback()
        {
            private static final long serialVersionUID = -5423095433535634321L;

            @Override
            public boolean onCloseButtonClicked(AjaxRequestTarget aTarget)
            {
                closeButtonClicked = true;
                return true;
            }
        });

        add(new AjaxLink<Void>("showOpenDocumentModal")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget target)
            {
                closeButtonClicked = false;
                openDocumentsModal.setContent(new OpenModalWindowPanel(openDocumentsModal
                        .getContentId(), bratAnnotatorModel, openDocumentsModal, Mode.ANNOTATION)
                {

                    private static final long serialVersionUID = -3434069761864809703L;

                    @Override
                    protected void onCancel(AjaxRequestTarget aTarget)
                    {
                        closeButtonClicked = true;
                    };
                });
                openDocumentsModal.setWindowClosedCallback(new ModalWindow.WindowClosedCallback()
                {
                    private static final long serialVersionUID = -1746088901018629567L;

                    @Override
                    public void onClose(AjaxRequestTarget target)
                    {
                        // A hack, the dialog opens for the first time, and if no document is
                        // selected window will be "blind down". Something in the brat js causes
                        // this!
                        if (bratAnnotatorModel.getProject() == null
                                || bratAnnotatorModel.getDocument() == null) {
                            setResponsePage(WelcomePage.class);
                        }

                        // Dialog was cancelled rather that a document was selected.
                        if (closeButtonClicked) {
                            return;
                        }
                        
                        loadDocumentAction(target);
                    }
                });
                // target.appendJavaScript("Wicket.Window.unloadConfirmation = false;");
                openDocumentsModal.show(target);
            }
        });

        add(new AnnotationLayersModalPanel("annotationLayersModalPanel",
                new Model<BratAnnotatorModel>(bratAnnotatorModel))
        {
            private static final long serialVersionUID = -4657965743173979437L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                // annotator.reloadContent(aTarget);
                aTarget.appendJavaScript(
                        "Wicket.Window.unloadConfirmation = false;" +
                        "window.location.reload()");

            }
        });

        add(new ExportModalPanel("exportModalPanel", new Model<BratAnnotatorModel>(
                bratAnnotatorModel)));
        // Show the previous document, if exist
        add(new AjaxLink<Void>("showPreviousDocument")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                // List of all Source Documents in the project
                List<SourceDocument> listOfSourceDocuements = repository
                        .listSourceDocuments(bratAnnotatorModel.getProject());

                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = repository.getUser(username);

                List<SourceDocument> sourceDocumentsinIgnorState = new ArrayList<SourceDocument>();
                for (SourceDocument sourceDocument : listOfSourceDocuements) {
                    if (repository.existsAnnotationDocument(sourceDocument, user)
                            && repository.getAnnotationDocument(sourceDocument, user).getState()
                                    .equals(AnnotationDocumentState.IGNORE)) {
                        sourceDocumentsinIgnorState.add(sourceDocument);
                    }
                }

                listOfSourceDocuements.removeAll(sourceDocumentsinIgnorState);

                // Index of the current source document in the list
                int currentDocumentIndex = listOfSourceDocuements.indexOf(bratAnnotatorModel
                        .getDocument());

                // If the first the document
                if (currentDocumentIndex == 0) {
                    aTarget.appendJavaScript("alert('This is the first document!')");
                    return;
                }
                bratAnnotatorModel.setDocumentName(listOfSourceDocuements.get(
                        currentDocumentIndex - 1).getName());
                bratAnnotatorModel
                        .setDocument(listOfSourceDocuements.get(currentDocumentIndex - 1));

                loadDocumentAction(aTarget);
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up }, EventType.click)));

        // Show the next document if exist
        add(new AjaxLink<Void>("showNextDocument")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                // List of all Source Documents in the project
                List<SourceDocument> listOfSourceDocuements = repository
                        .listSourceDocuments(bratAnnotatorModel.getProject());

                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = repository.getUser(username);

                List<SourceDocument> sourceDocumentsinIgnorState = new ArrayList<SourceDocument>();
                for (SourceDocument sourceDocument : listOfSourceDocuements) {
                    if (repository.existsAnnotationDocument(sourceDocument, user)
                            && repository.getAnnotationDocument(sourceDocument, user).getState()
                                    .equals(AnnotationDocumentState.IGNORE)) {
                        sourceDocumentsinIgnorState.add(sourceDocument);
                    }
                }

                listOfSourceDocuements.removeAll(sourceDocumentsinIgnorState);

                // Index of the current source document in the list
                int currentDocumentIndex = listOfSourceDocuements.indexOf(bratAnnotatorModel
                        .getDocument());

                // If the first document
                if (currentDocumentIndex == listOfSourceDocuements.size() - 1) {
                    aTarget.appendJavaScript("alert('This is the last document!')");
                    return;
                }
                bratAnnotatorModel.setDocumentName(listOfSourceDocuements.get(
                        currentDocumentIndex + 1).getName());
                bratAnnotatorModel
                        .setDocument(listOfSourceDocuements.get(currentDocumentIndex + 1));
                
                loadDocumentAction(aTarget);
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down }, EventType.click)));

        // Show the next page of this document
        add(new AjaxLink<Void>("showNext")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            /**
             * Get the current beginning sentence address and add on it the size of the display
             * window
             */
            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bratAnnotatorModel.getDocument() != null) {
                    JCas jCas = getJCas(bratAnnotatorModel.getProject(),
                            bratAnnotatorModel.getDocument());
                    int nextSentenceAddress = BratAjaxCasUtil
                            .getNextDisplayWindowSentenceBeginAddress(jCas,
                                    bratAnnotatorModel.getSentenceAddress(),
                                    bratAnnotatorModel.getWindowSize());
                    if (bratAnnotatorModel.getSentenceAddress() != nextSentenceAddress) {
                        bratAnnotatorModel.setSentenceAddress(nextSentenceAddress);

                        Sentence sentence = selectByAddr(jCas, Sentence.class, nextSentenceAddress);
                        bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                        bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        annotator.bratRenderLater(aTarget);
                        aTarget.add(numberOfPages);
                        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                                bratAnnotatorModel.getSentenceAddress())+1);
                        updateSentenceAddress();
                        aTarget.add(gotoPageTextField);
                    }

                    else {
                        aTarget.appendJavaScript("alert('This is last page!')");
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        // Show the previous page of this document
        add(new AjaxLink<Void>("showPrevious")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bratAnnotatorModel.getDocument() != null) {

                    JCas jCas = getJCas(bratAnnotatorModel.getProject(),
                            bratAnnotatorModel.getDocument());

                    int previousSentenceAddress = BratAjaxCasUtil
                            .getPreviousDisplayWindowSentenceBeginAddress(jCas,
                                    bratAnnotatorModel.getSentenceAddress(),
                                    bratAnnotatorModel.getWindowSize());
                    if (bratAnnotatorModel.getSentenceAddress() != previousSentenceAddress) {
                        bratAnnotatorModel.setSentenceAddress(previousSentenceAddress);

                        Sentence sentence = selectByAddr(jCas, Sentence.class,
                                previousSentenceAddress);
                        bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                        bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        annotator.bratRenderLater(aTarget);
                        aTarget.add(numberOfPages);
                        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                                bratAnnotatorModel.getSentenceAddress())+1);
                        updateSentenceAddress();
                        aTarget.add(gotoPageTextField);
                    }
                    else {
                        aTarget.appendJavaScript("alert('This is First Page!')");
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new AjaxLink<Void>("showFirst")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bratAnnotatorModel.getDocument() != null) {

                    JCas jCas = getJCas(bratAnnotatorModel.getProject(),
                            bratAnnotatorModel.getDocument());

                    if (bratAnnotatorModel.getFirstSentenceAddress() != bratAnnotatorModel
                            .getSentenceAddress()) {
                        bratAnnotatorModel.setSentenceAddress(bratAnnotatorModel
                                .getFirstSentenceAddress());

                        Sentence sentence = selectByAddr(jCas, Sentence.class,
                                bratAnnotatorModel.getFirstSentenceAddress());
                        bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                        bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());

                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        annotator.bratRenderLater(aTarget);
                        aTarget.add(numberOfPages);
                        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                                bratAnnotatorModel.getSentenceAddress())+1);
                        updateSentenceAddress();
                        aTarget.add(gotoPageTextField);
                    }
                    else {
                        aTarget.appendJavaScript("alert('This is first page!')");
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new AjaxLink<Void>("showLast")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (bratAnnotatorModel.getDocument() != null) {

                    JCas jCas = getJCas(bratAnnotatorModel.getProject(),
                            bratAnnotatorModel.getDocument());

                    int lastDisplayWindowBeginingSentenceAddress = BratAjaxCasUtil
                            .getLastDisplayWindowFirstSentenceAddress(
                                    getJCas(bratAnnotatorModel.getProject(),
                                            bratAnnotatorModel.getDocument()),
                                    bratAnnotatorModel.getWindowSize());
                    if (lastDisplayWindowBeginingSentenceAddress != bratAnnotatorModel
                            .getSentenceAddress()) {
                        bratAnnotatorModel
                                .setSentenceAddress(lastDisplayWindowBeginingSentenceAddress);

                        Sentence sentence = selectByAddr(jCas, Sentence.class,
                                lastDisplayWindowBeginingSentenceAddress);
                        bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                        bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());
                        aTarget.addChildren(getPage(), FeedbackPanel.class);
                        annotator.bratRenderLater(aTarget);
                        aTarget.add(numberOfPages);
                        gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                                bratAnnotatorModel.getSentenceAddress())+1);
                        updateSentenceAddress();
                        aTarget.add(gotoPageTextField);
                    }
                    else {
                        aTarget.appendJavaScript("alert('This is last Page!')");
                    }
                }
                else {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                }
            }
        }.add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new GuidelineModalPanel("guidelineModalPanel", new Model<BratAnnotatorModel>(
                bratAnnotatorModel)));

        gotoPageTextField = (NumberTextField<Integer>) new NumberTextField<Integer>("gotoPageText",
                new Model<Integer>(0));
        Form<Void> gotoPageTextFieldForm = new Form<Void>("gotoPageTextFieldForm");
        gotoPageTextFieldForm.add(new AjaxFormSubmitBehavior(gotoPageTextFieldForm, "onsubmit") {
			private static final long serialVersionUID = -4549805321484461545L;
			@Override
            protected void onSubmit(AjaxRequestTarget aTarget) {
				 if (gotoPageAddress == 0) {
	                    aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
	                    return;
	                }
				if (bratAnnotatorModel.getSentenceAddress() != gotoPageAddress) {
                    bratAnnotatorModel.setSentenceAddress(gotoPageAddress);

                    JCas jCas = getJCas(bratAnnotatorModel.getProject(),
                            bratAnnotatorModel.getDocument());

                    Sentence sentence = selectByAddr(jCas, Sentence.class, gotoPageAddress);
                    bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                    bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());

                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    annotator.bratRenderLater(aTarget);
                    aTarget.add(numberOfPages);
                    gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                            bratAnnotatorModel.getSentenceAddress())+1);
                    aTarget.add(gotoPageTextField);
                }
            }
        });
        gotoPageTextField.setType(Integer.class);
        gotoPageTextField.setMinimum(1);
        gotoPageTextField.setDefaultModelObject(1);
        add(gotoPageTextFieldForm.add(gotoPageTextField));

        gotoPageTextField.add(new AjaxFormComponentUpdatingBehavior("onchange")
        {
            private static final long serialVersionUID = 56637289242712170L;

            @Override
            protected void onUpdate(AjaxRequestTarget target)
            {
                if (gotoPageTextField.getModelObject() < 1) {
                    target.appendJavaScript("alert('Page number shouldn't be less than 1')");
                }
                else {
                    updateSentenceAddress();
                }

            }
        });

        add(new AjaxLink<Void>("gotoPageLink")
        {
            private static final long serialVersionUID = 7496156015186497496L;

            @Override
            public void onClick(AjaxRequestTarget aTarget)
            {
                if (gotoPageAddress == 0) {
                    aTarget.appendJavaScript("alert('The sentence number entered is not valid')");
                    return;
                }
                if (bratAnnotatorModel.getDocument() == null) {
                    aTarget.appendJavaScript("alert('Please open a document first!')");
                    return;
                }
                if (bratAnnotatorModel.getSentenceAddress() != gotoPageAddress) {
                    bratAnnotatorModel.setSentenceAddress(gotoPageAddress);

                    JCas jCas = getJCas(bratAnnotatorModel.getProject(),
                            bratAnnotatorModel.getDocument());

                    Sentence sentence = selectByAddr(jCas, Sentence.class, gotoPageAddress);
                    bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                    bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());

                    aTarget.addChildren(getPage(), FeedbackPanel.class);
                    annotator.bratRenderLater(aTarget);
                    aTarget.add(numberOfPages);
                    gotoPageTextField.setModelObject(BratAjaxCasUtil.getFirstSentenceNumber(jCas,
                            bratAnnotatorModel.getSentenceAddress())+1);
                    aTarget.add(gotoPageTextField);
                }
            }
        });

        finish = new FinishImage("finishImage", new Model<BratAnnotatorModel>(bratAnnotatorModel));
        finish.setOutputMarkupId(true);

        add(new FinishLink("showYesNoModalPanel",
                new Model<BratAnnotatorModel>(bratAnnotatorModel), finish)
        {
            private static final long serialVersionUID = -4657965743173979437L;
        });
    }

    private void updateSentenceAddress()
    {
        gotoPageAddress = BratAjaxCasUtil.getSentenceAddress(
                getJCas(bratAnnotatorModel.getProject(), bratAnnotatorModel.getDocument()),
                gotoPageTextField.getModelObject());
    }

    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);
        
        String jQueryString = "";
        if (firstLoad) {
            jQueryString += "jQuery('#showOpenDocumentModal').trigger('click');";
            firstLoad = false;
        }
        response.render(OnLoadHeaderItem.forScript(jQueryString));
    }

    private JCas getJCas(Project aProject, SourceDocument aDocument)
    {
        JCas jCas = null;
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();

            User user = repository.getUser(username);
            jCas = repository.readJCas(aDocument, aProject, user);
        }
        catch (UIMAException e) {
            error("CAS object not found :" + ExceptionUtils.getRootCauseMessage(e));
        }
        catch (IOException e) {
            error("CAS object not found :" + ExceptionUtils.getRootCauseMessage(e));
        }
        catch (ClassNotFoundException e) {
            error("The Class name in the properties is not found " + ":"
                    + ExceptionUtils.getRootCauseMessage(e));
        }
        return jCas;

    }

    public int getWindowSize()
    {
        return windowSize;
    }

    public void setWindowSize(int aWindowSize)
    {
        windowSize = aWindowSize;
    }

    private void loadDocumentAction(AjaxRequestTarget target)
    {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            repository.upgradeCasAndSave(bratAnnotatorModel.getDocument(), Mode.ANNOTATION,
                    username);

            bratAnnotatorModel.setUser(repository.getUser(username));
            JCas jCas = getCas(bratAnnotatorModel.getProject(), bratAnnotatorModel.getUser(),
                    bratAnnotatorModel.getDocument());
            if (bratAnnotatorModel.getSentenceAddress() == -1
                    || bratAnnotatorModel.getDocument().getId() != currentDocumentId
                    || bratAnnotatorModel.getProject().getId() != currentprojectId) {

                bratAnnotatorModel
                        .setSentenceAddress(BratAjaxCasUtil.getFirstSentenceAddress(jCas));
                bratAnnotatorModel.setLastSentenceAddress(BratAjaxCasUtil
                        .getLastSentenceAddress(jCas));
                bratAnnotatorModel.setFirstSentenceAddress(bratAnnotatorModel.getSentenceAddress());
                bratAnnotatorModel.setWindowSize(5);

                ProjectUtil.setAnnotationPreference(username, repository, annotationService,
                        bratAnnotatorModel, Mode.ANNOTATION);

                Sentence sentence = selectByAddr(jCas, Sentence.class,
                        bratAnnotatorModel.getSentenceAddress());
                bratAnnotatorModel.setSentenceBeginOffset(sentence.getBegin());
                bratAnnotatorModel.setSentenceEndOffset(sentence.getEnd());

                LOG.debug("Configured BratAnnotatorModel for user [" + username + "] f:["
                        + bratAnnotatorModel.getFirstSentenceAddress() + "] l:["
                        + bratAnnotatorModel.getLastSentenceAddress() + "] s:["
                        + bratAnnotatorModel.getSentenceAddress() + "]");
            }

            // if project is changed, reset some project specific settings
            if (currentprojectId != bratAnnotatorModel.getProject().getId()) {
                bratAnnotatorModel.setRememberedArcFeatures(null);
                bratAnnotatorModel.setRememberedArcLayer(null);
                bratAnnotatorModel.setRememberedSpanFeatures(null);
                bratAnnotatorModel.setRememberedSpanLayer(null);
                // bratAnnotatorModel.setMessage(null);
            }

            currentprojectId = bratAnnotatorModel.getProject().getId();
            currentDocumentId = bratAnnotatorModel.getDocument().getId();

            updateSentenceAddress();

            // Wicket-level rendering of annotator because it becomes visible
            // after selecting a document
            target.add(annotator);

            // brat-level initialization and rendering of document
            annotator.bratInit(target);
            annotator.bratRender(target);
        }
        catch (DataRetrievalFailureException e) {
            target.addChildren(getPage(), FeedbackPanel.class);
            error(e.getMessage());
        }
        catch (IOException e) {
            target.addChildren(getPage(), FeedbackPanel.class);
            error(e.getMessage());
        }
        catch (UIMAException e) {
            target.addChildren(getPage(), FeedbackPanel.class);
            error(ExceptionUtils.getRootCauseMessage(e));
        }
        catch (ClassNotFoundException e) {
            target.addChildren(getPage(), FeedbackPanel.class);
            error(e.getMessage());
        }

        // Update dynamic elements in action bar
        target.add(finish);
        target.add(numberOfPages);
        target.add(documentNamePanel);
    }

    private JCas getCas(Project aProject, User user, SourceDocument aDocument)
        throws UIMAException, IOException, ClassNotFoundException
    {
        JCas jCas = null;
        jCas = repository.readJCas(aDocument, aProject, user);
        return jCas;
    }

}
