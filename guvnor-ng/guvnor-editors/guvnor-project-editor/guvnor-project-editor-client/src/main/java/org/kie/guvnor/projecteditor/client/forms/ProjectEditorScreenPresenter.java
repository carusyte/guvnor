/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.guvnor.projecteditor.client.forms;

import javax.enterprise.inject.New;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.ioc.client.api.Caller;
import org.kie.guvnor.commons.service.builder.BuildService;
import org.kie.guvnor.commons.ui.client.callbacks.HasBusyIndicatorDefaultErrorCallback;
import org.kie.guvnor.commons.ui.client.popups.file.CommandWithCommitMessage;
import org.kie.guvnor.commons.ui.client.popups.file.SaveOperationService;
import org.kie.guvnor.commons.ui.client.resources.i18n.CommonConstants;
import org.kie.guvnor.project.service.KModuleService;
import org.kie.guvnor.projecteditor.client.resources.i18n.ProjectEditorConstants;
import org.kie.guvnor.projecteditor.client.type.POMResourceType;
import org.kie.guvnor.services.metadata.MetadataService;
import org.kie.guvnor.services.metadata.model.Metadata;
import org.uberfire.backend.vfs.Path;
import org.uberfire.client.annotations.OnStart;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.mvp.Command;
import org.uberfire.client.workbench.widgets.menu.MenuFactory;
import org.uberfire.client.workbench.widgets.menu.Menus;
import org.uberfire.shared.mvp.PlaceRequest;

@WorkbenchEditor(identifier = "projectEditorScreen", supportedTypes = { POMResourceType.class })
public class
        ProjectEditorScreenPresenter
        implements ProjectEditorScreenView.Presenter {

    private ProjectEditorScreenView view;
    private POMEditorPanel pomPanel;
    private KModuleEditorPanel kModuleEditorPanel;
    private Caller<KModuleService> kModuleServiceCaller;
    private Caller<BuildService> buildServiceCaller;

    private Path pathToPomXML;
    private Path pathToKModuleXML;
    private Caller<MetadataService> metadataService;
    private Metadata kmoduleMetadata;
    private Metadata pomMetadata;
    private SaveOperationService saveOperationService;

    private Menus menus;
    private boolean isReadOnly;

    public ProjectEditorScreenPresenter() {
    }

    @Inject
    public ProjectEditorScreenPresenter( @New ProjectEditorScreenView view,
                                         @New POMEditorPanel pomPanel,
                                         @New KModuleEditorPanel kModuleEditorPanel,
                                         @New RunAllTestScenariosEditor runAllTestScenariosEditor,
                                         Caller<KModuleService> kModuleServiceCaller,
                                         Caller<BuildService> buildServiceCaller,
                                         Caller<MetadataService> metadataService,
                                         SaveOperationService saveOperationService ) {
        this.view = view;
        this.pomPanel = pomPanel;
        this.kModuleEditorPanel = kModuleEditorPanel;
        this.kModuleServiceCaller = kModuleServiceCaller;
        this.buildServiceCaller = buildServiceCaller;
        this.metadataService = metadataService;
        this.saveOperationService = saveOperationService;

        view.setPresenter( this );
        view.setPOMEditorPanel( pomPanel );
        view.setTestScenarioPanel( runAllTestScenariosEditor );        
    }

    @OnStart
    public void init( final Path path,
                      final PlaceRequest request ) {

        this.isReadOnly = request.getParameter( "readOnly", null ) == null ? false : true;
        pathToPomXML = path;

        view.showBusyIndicator( CommonConstants.INSTANCE.Loading() );

        pomPanel.init( path,
                       isReadOnly );

        if ( !isReadOnly ) {
            addKModuleEditor();
        }

        view.hideBusyIndicator();

        makeMenuBar();
    }

    private void makeMenuBar() {
        menus = MenuFactory
                .newTopLevelMenu( CommonConstants.INSTANCE.File() )
                .menus()
                .menu( CommonConstants.INSTANCE.Save() )
                .respondsWith( new Command() {
                    @Override
                    public void execute() {
                        saveOperationService.save( pathToPomXML,
                                                   new CommandWithCommitMessage() {
                                                       @Override
                                                       public void execute( final String comment ) {
                                                           view.showBusyIndicator( CommonConstants.INSTANCE.Saving() );
                                                           // We need to use callback here or jgit will break when we save two files at the same time.
                                                           pomPanel.save( comment,
                                                                          new Command() {
                                                                              @Override
                                                                              public void execute() {
                                                                                  if ( kModuleEditorPanel.hasBeenInitialized() ) {
                                                                                      kModuleEditorPanel.save( comment,
                                                                                                               kmoduleMetadata );
                                                                                  }
                                                                                  view.hideBusyIndicator();
                                                                              }
                                                                          }, pomMetadata );
                                                       }
                                                   } );
                    }
                } )
                .endMenu()
                .endMenus()
                .endMenu()
                .newTopLevelMenu( view.getBuildMenuItemText() )
                .respondsWith( new Command() {
                    @Override
                    public void execute() {
                        view.showBusyIndicator( ProjectEditorConstants.INSTANCE.Building() );
                        buildServiceCaller.call( getBuildSuccessCallback(),
                                                 new HasBusyIndicatorDefaultErrorCallback( view ) ).buildAndDeploy( pathToPomXML );
                    }
                } )
                .endMenu().build();

// For now every module is a kie project.
//        if (pathToKModuleXML == null) {
//            menus.addItem(new DefaultMenuItemCommand(
//                    view.getEnableKieProjectMenuItemText(),
//                    new Command() {
//                        @Override
//                        public void execute() {
//                            projectEditorServiceCaller.call(
//                                    new RemoteCallback<Path>() {
//                                        @Override
//                                        public void callback(Path pathToKProject) {
//                                            pathToKModuleXML = pathToKProject;
//                                            setUpKProject(pathToKProject);
//                                        }
//                                    }
//                            ).setUpKModuleStructure(pathToPomXML);
//                        }
//                    }
//            ));
//        }

    }

    private RemoteCallback getBuildSuccessCallback() {
        return new RemoteCallback<Void>() {
            @Override
            public void callback( final Void v ) {
                view.hideBusyIndicator();
            }
        };
    }

    private void addKModuleEditor() {
        kModuleServiceCaller.call( getResolveKModulePathSuccessCallback(),
                                   new HasBusyIndicatorDefaultErrorCallback( view ) ).pathToRelatedKModuleFileIfAny( pathToPomXML );
    }

    private RemoteCallback<Path> getResolveKModulePathSuccessCallback() {
        return new RemoteCallback<Path>() {

            @Override
            public void callback( final Path pathToKModuleXML ) {
                ProjectEditorScreenPresenter.this.pathToKModuleXML = pathToKModuleXML;
                if ( pathToKModuleXML != null ) {
                    view.setKModuleEditorPanel( kModuleEditorPanel );
                }
            }
        };
    }

    @WorkbenchPartTitle
    public String getTitle() {
        //TODO {porcelli} Needs a way to notify title updates -> return pomPanel.getTitle();
        return "Project Editor [" + pathToPomXML.getFileName() + "]";
    }

    @WorkbenchPartView
    public Widget asWidget() {
        return view.asWidget();
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return menus;
    }

    @Override
    public void onPOMMetadataTabSelected() {
        if ( pomMetadata == null ) {
            view.showBusyIndicator( CommonConstants.INSTANCE.Loading() );
            metadataService.call( getPOMMetadataSuccessCallback(),
                                  new HasBusyIndicatorDefaultErrorCallback( view ) ).getMetadata( pathToPomXML );
        }
    }

    @Override
    public void onKModuleTabSelected() {
        if ( !kModuleEditorPanel.hasBeenInitialized() ) {
            kModuleEditorPanel.init( pathToKModuleXML, false );
        }
    }

    @Override
    public void onKModuleMetadataTabSelected() {
        if ( kmoduleMetadata == null ) {
            view.showBusyIndicator( CommonConstants.INSTANCE.Loading() );
            metadataService.call( getKModuleMetadataSuccessCallback(),
                                  new HasBusyIndicatorDefaultErrorCallback( view ) ).getMetadata( pathToKModuleXML );
        }
    }

    private RemoteCallback<Metadata> getPOMMetadataSuccessCallback() {
        return new RemoteCallback<Metadata>() {

            @Override
            public void callback( final Metadata metadata ) {
                pomMetadata = metadata;
                view.hideBusyIndicator();
                view.setPOMMetadata( metadata );
            }
        };
    }

    private RemoteCallback<Metadata> getKModuleMetadataSuccessCallback() {
        return new RemoteCallback<Metadata>() {

            @Override
            public void callback( final Metadata metadata ) {
                kmoduleMetadata = metadata;
                view.hideBusyIndicator();
                view.setKModuleMetadata( metadata );
            }
        };
    }

}
