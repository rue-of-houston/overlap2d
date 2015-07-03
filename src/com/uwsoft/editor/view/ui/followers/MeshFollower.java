/*
 * ******************************************************************************
 *  * Copyright 2015 See AUTHORS file.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *****************************************************************************
 */

package com.uwsoft.editor.view.ui.followers;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pools;
import com.kotcrab.vis.ui.VisUI;
import com.uwsoft.editor.renderer.components.MeshComponent;
import com.uwsoft.editor.renderer.physics.PhysicsBodyLoader;
import com.uwsoft.editor.utils.runtime.ComponentRetriever;
import com.uwsoft.editor.view.stage.Sandbox;
import javafx.scene.shape.Mesh;

import java.util.ArrayList;

/**
 * Created by azakhary on 7/2/2015.
 */
public class MeshFollower extends SubFollower {

    private MeshComponent meshComponent;

    private ArrayList<Vector2> originalPoints;
    private Vector2[] drawPoints;
    protected Actor[] anchors;

    private ShapeRenderer shapeRenderer;

    public static final int POINT_WIDTH = 8;
    public static final int CIRCLE_RADIUS = 10;

    private static final Color outlineColor = new Color(200f / 255f, 156f / 255f, 71f / 255f, 1f);
    private static final Color innerColor = new Color(200f / 255f, 200f / 255f, 200f / 255f, 1f);
    private static final Color overColor = new Color(255f / 255f, 94f / 255f, 0f / 255f, 1f);

    private int lineIndex;
    public int draggingAnchorId = -1;

    public MeshFollower(Entity entity) {
        super(entity);
        setTouchable(Touchable.enabled);
    }

    public void create() {
        meshComponent = ComponentRetriever.get(entity, MeshComponent.class);

        shapeRenderer = new ShapeRenderer();
    }

    @Override
    protected void setStage(Stage stage) {
        super.setStage(stage);
        if (stage != null) {
            shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
        }
    }

    public void update() {
        if(meshComponent != null && meshComponent.vertices != null) {
            computeOriginalPoints();
            computeDrawPoints();
            initAnchors();
        }
    }

    public void updateDraw() {
        computeDrawPoints();
        initAnchors();
    }

    private void computeOriginalPoints() {
        originalPoints = new ArrayList<>();
        if(meshComponent == null) return;
        for (Vector2[] poly : meshComponent.vertices) {
            for (int i = 0; i < poly.length; i++) {
                if (!originalPoints.contains(poly[i]))
                    originalPoints.add(poly[i]);
            }
        }

    }

    private void computeDrawPoints() {
        drawPoints = originalPoints.toArray(new Vector2[0]);
    }


    @Override
    public void draw (Batch batch, float parentAlpha) {
        if(meshComponent != null && meshComponent.vertices != null) {
            positionAnchors();
            batch.end();

            Gdx.gl.glLineWidth(1);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(getStage().getCamera().combined);
            shapeRenderer.setTransformMatrix(batch.getTransformMatrix());

            drawTriangulatedPolygons();
            drawOutlines();

            Gdx.gl.glDisable(GL20.GL_BLEND);

            batch.begin();

            drawPoints(batch, parentAlpha);
        }
    }

    public void drawOutlines() {
        if (drawPoints.length > 0) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (int i = 1; i < drawPoints.length; i++) {
                shapeRenderer.setColor(outlineColor);
                if (lineIndex == i) {
                    shapeRenderer.setColor(overColor);
                }
                shapeRenderer.line(drawPoints[i], drawPoints[i - 1]);
            }
            shapeRenderer.setColor(outlineColor);
            if(lineIndex == 0) {
                shapeRenderer.setColor(overColor);
            }
            shapeRenderer.line(drawPoints[drawPoints.length - 1], drawPoints[0]);
            shapeRenderer.end();
        }

    }

    public void drawTriangulatedPolygons() {
        if (meshComponent.vertices == null) {
            return;
        }
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(innerColor);
        for (Vector2[] poly : meshComponent.vertices) {
            for (int i = 1; i < poly.length; i++) {
                shapeRenderer.line(poly[i - 1], poly[i]);
            }
            if (poly.length > 0)
                shapeRenderer.line(poly[poly.length - 1].x, poly[poly.length - 1].y, poly[0].x, poly[0].y);
        }
        shapeRenderer.end();
    }

    public void drawPoints(Batch batch, float parentAlpha) {
        for (int i = 0; i < anchors.length; i++) {
            anchors[i].draw(batch, parentAlpha);
        }
    }

    private void positionAnchors() {
        for (int i = 0; i < anchors.length; i++) {
            anchors[i].setX(Math.round(originalPoints.get(i).x - anchors[i].getWidth()/2f));
            anchors[i].setY(Math.round(originalPoints.get(i).y - anchors[i].getHeight() / 2f));
        }
    }

    private void initAnchors() {
        anchors = new Actor[originalPoints.size()];
        for (int i = 0; i < originalPoints.size(); i++) {
            anchors[i] = getMiniRect();
        }
    }

    private Image getMiniRect() {
        Image rect = new Image(VisUI.getSkin().getDrawable("selection-anchor"));
        int w = (int) (rect.getWidth()/2);
        int h = (int) (rect.getHeight()/2);
        rect.setOrigin(w, h);
        return rect;
    }

    public void setListener(MeshTransformationListener listener) {
        clearListeners();
        addListener(new ClickListener() {

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                super.touchDown(event, x, y, pointer, button);
                int anchorId = anchorHitTest(x, y);
                if (anchorId >= 0) {
                    draggingAnchorId = anchorId;
                    listener.anchorDown(MeshFollower.this, anchorId, x, y);
                } else if(lineIndex > -1) {
                    // not anchor but line is selected gotta make new point
                    listener.vertexDown(MeshFollower.this, lineIndex, x, y);
                }
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                int anchorId = draggingAnchorId;
                if (anchorId >= 0) {
                    listener.anchorDragged(MeshFollower.this, anchorId, x, y);
                } else if(lineIndex > -1) {

                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                int anchorId = anchorHitTest(x, y);
                if (anchorId >= 0) {
                    listener.anchorDown(MeshFollower.this, anchorId, x, y);
                } else if(lineIndex > -1) {
                    listener.vertexUp(MeshFollower.this, lineIndex, x, y);
                }
                draggingAnchorId = -1;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                if (lineIndex > -1) {
                    System.out.println(lineIndex);
                }

                return super.mouseMoved(event, x, y);
            }
        });
    }

    @Override
    public Actor hit (float x, float y, boolean touchable) {
        if(anchors == null) return null;
        for (int i = 0; i < anchors.length; i++) {
            if(anchors[i].hit(x-anchors[i].getX(), y - anchors[i].getY(), true) != null) {
                return this;
            }
        }

        // checking for vertex intersect
        Vector2 tmpVector = new Vector2(x, y);
        lineIndex = -1;
        for (int i = 1; i < drawPoints.length; i++) {
            if (Intersector.intersectSegmentCircle(drawPoints[i - 1], drawPoints[i], tmpVector, CIRCLE_RADIUS)) {
                lineIndex = i;
                break;
            }
        }
        if (drawPoints.length > 0 && Intersector.intersectSegmentCircle(drawPoints[drawPoints.length - 1], drawPoints[0], tmpVector, CIRCLE_RADIUS)) {
            lineIndex = 0;
        }
        if(lineIndex >= 0) {
            return this;
        }

        return null;
    }

    private int anchorHitTest(float x, float y) {
        for (int i = 0; i < anchors.length; i++) {
            if(anchors[i].hit(x-anchors[i].getX(), y - anchors[i].getY(), true) != null) {
                return i;
            }
        }
        return -1;
    }

    public Entity getEntity() {
        return entity;
    }

    public ArrayList<Vector2> getOriginalPoints() {
        return originalPoints;
    }
}
