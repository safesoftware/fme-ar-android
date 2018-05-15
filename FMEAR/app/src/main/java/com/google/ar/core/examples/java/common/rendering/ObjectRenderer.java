/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.java.common.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.safe.fmear.FileFinder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import de.javagl.obj.FloatTuple;
import de.javagl.obj.FloatTuples;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlReader;
import de.javagl.obj.Obj;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjFaces;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjSplitting;
import de.javagl.obj.ObjUtils;
import de.javagl.obj.Objs;

/** Renders an object loaded from an OBJ file in OpenGL. */
public class ObjectRenderer {
  private static final String TAG = ObjectRenderer.class.getSimpleName();

  /**
   * Blend mode.
   *
   * @see #setBlendMode(BlendMode)
   */
  public enum BlendMode {
    /** Multiplies the destination color by the source alpha. */
    Shadow,
    /** Normal alpha blending. */
    Grid
  }

  public class Bounds {

    public boolean isValid() {
      return initialized;
    }

    public void reset() {
      set(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
      initialized = false;
    }

    public void set(float newMinX, float newMinY, float newMinZ, float newMaxX, float newMaxY, float newMaxZ) {
      minX = newMinX;
      minY = newMinY;
      minZ = newMinZ;
      maxX = newMaxX;
      maxY = newMaxY;
      maxZ = newMaxZ;
      initialized = true;
    }

    public void expandBy(Bounds otherBounds) {
      if (!otherBounds.isValid()) {
        return;
      }

      if (initialized) {
        minX = Math.min(minX, otherBounds.getMinX());
        minY = Math.min(minY, otherBounds.getMinY());
        minZ = Math.min(minZ, otherBounds.getMinZ());
        maxX = Math.max(maxX, otherBounds.getMaxX());
        maxY = Math.max(maxY, otherBounds.getMaxY());
        maxZ = Math.max(maxZ, otherBounds.getMaxZ());
      } else {
        minX = otherBounds.getMinX();
        minY = otherBounds.getMinY();
        minZ = otherBounds.getMinZ();
        maxX = otherBounds.getMaxX();
        maxY = otherBounds.getMaxY();
        maxZ = otherBounds.getMaxZ();
        initialized = true;
      }
    }

    public float getMinX() { return minX; };
    public float getMinY() { return minY; };
    public float getMinZ() { return minZ; };
    public float getMaxX() { return maxX; };
    public float getMaxY() { return maxY; };
    public float getMaxZ() { return maxZ; };

    public float[] getCenter() {
      return new float[] {
              (getMinX() + getMaxX()) * 0.5f,
              (getMinY() + getMaxY()) * 0.5f,
              (getMinZ() + getMaxZ()) * 0.5f
      };
    }

    public float[] getSize() {
      return new float[] {
              (getMaxX() - getMinX()),
              (getMaxY() - getMinY()),
              (getMaxZ() - getMinZ())
      };
    }

    boolean initialized = false;
    public float minX = 0.0f;
    public float minY = 0.0f;
    public float minZ = 0.0f;
    public float maxX = 0.0f;
    public float maxY = 0.0f;
    public float maxZ = 0.0f;
  };

  public class ObjProperty {

    class MaterialProperty
    {
      public String materialName;

      public Bounds bounds = new Bounds();

      public boolean hasTexture = false;
      public int textureId = 0;

      public int vertexBufferId = 0;
      public int indexBufferId = 0;

      public int verticesBaseAddress = 0;
      public int texCoordsBaseAddress = 0;
      public int normalsBaseAddress = 0;

      public int numVertices = 0;
      public int numNormals = 0;
      public int numTexCoords = 0;
      public int indexCount = 0;

      // matches the default values in DefaultMtl.java
      private FloatTuple ambient = FloatTuples.create(0f, 0f, 0f);
      private FloatTuple diffuse = FloatTuples.create(0f, 0f, 0f);
      private FloatTuple specular = FloatTuples.create(0f, 0f, 0f);
      private float shininess = 100f;
    }

    public String objFilename;
    public Bounds bounds = new Bounds();
    public ArrayList<MaterialProperty> materialProperties;
  };

  // Shader names.
  private static final String VERTEX_SHADER_NAME = "shaders/object.vert";
  private static final String FRAGMENT_SHADER_NAME = "shaders/object.frag";

  private static final int COORDS_PER_VERTEX = 3;

  // Note: the last component must be zero to avoid applying the translational part of the matrix.
  private static final float[] LIGHT_DIRECTION = new float[] {0.250f, 0.866f, 0.433f, 0.0f};
  private final float[] viewLightDirection = new float[4];

  private int program;

  private List<ObjProperty> objProperties;
  private Bounds datasetBounds = new Bounds();

  // Shader location: model view projection matrix.
  private int modelViewUniform;
  private int modelViewProjectionUniform;

  // Shader location: object attributes.
  private int positionAttribute;
  private int normalAttribute;
  private int texCoordAttribute;

  // Shader location: texture sampler.
  private int textureUniform;

  // Shader location: environment properties.
  private int lightingParametersUniform;

  // Shader location: material properties.
  private int materialAmbientUniform;
  private int materialDiffuseUniform;
  private int materialSpecularUniform;
  private int materialShininessUniform;

  // Shader location: color correction property
  private int colorCorrectionParameterUniform;

  private BlendMode blendMode = null;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];

  private boolean initialized = false;


  public ObjectRenderer() {
    Matrix.setIdentityM(modelMatrix, 0);
  }

  // The original createOnGlThread create the shaders, the program, and the geometries. This
  // createProgram function only creates the shaders and the program. We can call this function
  // once in onSurfaceCreated. Then, when we load a dataset, we can call the following loadObj
  // function, which defines the geometries.
  public void createProgram(Context context)
          throws IOException {

    final int vertexShader =
            ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
    final int fragmentShader =
            ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

    program = GLES20.glCreateProgram();
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);
    GLES20.glLinkProgram(program);
    GLES20.glUseProgram(program);

    ShaderUtil.checkGLError(TAG, "Program creation");

    modelViewUniform = GLES20.glGetUniformLocation(program, "u_ModelView");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

    positionAttribute = GLES20.glGetAttribLocation(program, "a_Position");
    normalAttribute = GLES20.glGetAttribLocation(program, "a_Normal");
    texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord");

    textureUniform = GLES20.glGetUniformLocation(program, "u_Texture");

    lightingParametersUniform = GLES20.glGetUniformLocation(program, "u_LightingParameters");
    materialAmbientUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters.ambient");
    materialDiffuseUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters.diffuse");
    materialSpecularUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters.specular");
    materialShininessUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters.shininess");
    colorCorrectionParameterUniform =
            GLES20.glGetUniformLocation(program, "u_ColorCorrectionParameters");

    ShaderUtil.checkGLError(TAG, "Program parameters");

    Matrix.setIdentityM(modelMatrix, 0);

  }

  public Bounds calculateBounds(FloatBuffer vertices) {
    Bounds bounds = new Bounds();
    int dimensions = 3;
    int limit = vertices.limit();
    int index = 0;
    if (limit >= /*xyz*/ dimensions) {
      bounds.minX = vertices.get(index++);
      bounds.minY = vertices.get(index++);
      bounds.minZ = vertices.get(index++);
      bounds.maxX = bounds.minX;
      bounds.maxY = bounds.minY;
      bounds.maxZ = bounds.minZ;
      bounds.initialized = true;
      while ((index + dimensions) <= limit) {
        bounds.minX = Math.min(bounds.minX, vertices.get(index));
        bounds.minY = Math.min(bounds.minY, vertices.get(index + 1));
        bounds.minZ = Math.min(bounds.minZ, vertices.get(index + 2));
        bounds.maxX = Math.max(bounds.maxX, vertices.get(index));
        bounds.maxY = Math.max(bounds.maxY, vertices.get(index + 1));
        bounds.maxZ = Math.max(bounds.maxZ, vertices.get(index + 2));
        index += dimensions;
      }
    }
    return bounds;
  }

  public void loadObjFiles(Context context, List<File> files)
          throws IOException {

    if (files.isEmpty()) {
      return;
    }

    // TODO: Temporarily using this boolean to decide whether we want to draw or not.
    initialized = true;

    objProperties = new ArrayList<>(files.size());
    datasetBounds.reset();

    // Read each obj file
    for (File objFile : files)
    {
      // Read the obj file.
      try (InputStream objInputStream = context.getContentResolver().openInputStream(Uri.fromFile(objFile))) {
        if (objInputStream == null) {
          // nothing to load, move on
          return;
        }

        Obj objObject = ObjReader.read(objInputStream);
        Map<String, MtlAndTexture> materialsByName = fetchMaterials(objObject, context, objFile.getParentFile());

        // Prepare the Obj so that its structure is suitable for
        // rendering with OpenGL:
        // 1. Triangulate it
        // 2. Make sure that texture coordinates are not ambiguous
        // 3. Make sure that normals are not ambiguous
        // 4. Convert it to single-indexed data
        objObject = ObjUtils.convertToRenderable(objObject);

        // For every obj file, store the properties for later use
        ObjProperty objProperty = new ObjProperty();
        objProperty.objFilename = objFile.toString();
        objProperties.add(objProperty);

        Map<String, Obj> materialToObjMap = ObjSplitting.splitByMaterialGroups(objObject);
        if (materialToObjMap.isEmpty()) {
          // If there is no material, we just simply add the original obj with an empty material
          // name
          materialToObjMap.put("", objObject);
        }

        int numMaterialGroups = materialToObjMap.size();
        objProperty.materialProperties = new ArrayList<>(numMaterialGroups);

        for (Map.Entry<String, Obj> entry : materialToObjMap.entrySet()) {

          // Get the material name from the key and the obj object from the value
          String materialName = entry.getKey();
          Obj currObj = entry.getValue();

          if (currObj.getNumNormals() <= 0) {
            currObj = createNewObjWithNormals(currObj);
          }

          // Create a material property record in the obj property
          ObjProperty.MaterialProperty materialProperty = objProperty.new MaterialProperty();
          objProperty.materialProperties.add(materialProperty);
          materialProperty.materialName = materialName;

          // If we can read a material and or a texture, we store it in the property.
          MtlAndTexture mtlAndTexture = materialsByName.get(materialName);
          if (mtlAndTexture != null) {
            Mtl material = mtlAndTexture.getMtl();
            File textureFile = mtlAndTexture.getTextureFile();
            if (textureFile != null && textureFile.exists()) {
              materialProperty.textureId = loadTexture(context, textureFile);
              materialProperty.hasTexture = true;
            }
            if (material != null) {
              materialProperty.ambient = material.getKa();
              materialProperty.diffuse = material.getKd();
              materialProperty.specular = material.getKs();

              materialProperty.shininess = material.getNs();
            }
          }

          // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
          // that OpenGL understands.

          // Obtain the data from the OBJ, as direct buffers:
          IntBuffer wideIndices = ObjData.getFaceVertexIndices(currObj, 3);
          FloatBuffer vertices = ObjData.getVertices(currObj);
          FloatBuffer texCoords = ObjData.getTexCoords(currObj, 2);

          FloatBuffer normals = ObjData.getNormals(currObj);

          // Calculate the material property bounds. Also expand the obj property bounds.
          materialProperty.bounds = calculateBounds(vertices);
          objProperty.bounds.expandBy(materialProperty.bounds);

          // Convert int indices to shorts for GL ES 2.0 compatibility
          ShortBuffer indices = ByteBuffer.allocateDirect(2 * wideIndices.limit())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
          while (wideIndices.hasRemaining()) {
            indices.put((short) wideIndices.get());
          }
          indices.rewind();

          int[] buffers = new int[2];
          GLES20.glGenBuffers(2, buffers, 0);
          materialProperty.vertexBufferId = buffers[0];
          materialProperty.indexBufferId = buffers[1];

          // Load vertex buffer
          materialProperty.numVertices = vertices.limit() / 3;
          materialProperty.numTexCoords = texCoords.limit() / 3;
          materialProperty.numNormals = normals.limit() / 3;

          materialProperty.verticesBaseAddress = 0;
          materialProperty.texCoordsBaseAddress = materialProperty.verticesBaseAddress + 4 * vertices.limit();
          materialProperty.normalsBaseAddress = materialProperty.texCoordsBaseAddress + 4 * texCoords.limit();
          final int totalBytes = materialProperty.normalsBaseAddress + 4 * normals.limit();

          GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, materialProperty.vertexBufferId);
          GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW);
          if (vertices.limit() > 0) {
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER, materialProperty.verticesBaseAddress, 4 * vertices.limit(), vertices);
          }
          if (texCoords.limit() > 0) {
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER, materialProperty.texCoordsBaseAddress, 4 * texCoords.limit(), texCoords);
          }
          if (normals.limit() > 0) {
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER, materialProperty.normalsBaseAddress, 4 * normals.limit(), normals);
          }
          GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

          // Load index buffer
          materialProperty.indexCount = indices.limit();
          if (materialProperty.indexCount > 0) {
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, materialProperty.indexBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * materialProperty.indexCount, indices, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
          }

          ShaderUtil.checkGLError(TAG, "OBJ buffer load");
        }

        datasetBounds.expandBy(objProperty.bounds);
      }
    }
  }

  public Bounds getDatasetBounds() { return datasetBounds; };


  private int loadTexture(Context context, File textureFile) throws IOException {
    final int[] textureHandle = new int[]{0};
    GLES20.glGenTextures(textureHandle.length, textureHandle, 0);
    if (textureHandle[0] == 0) {
      throw new RuntimeException("Error generating texture handle.");
    }

    try (InputStream textureInputStream = context.getContentResolver().openInputStream(Uri.fromFile(textureFile))) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inScaled = false;
      Bitmap textureBitmap =
              BitmapFactory.decodeStream(textureInputStream, null, options);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
      textureBitmap.recycle();

      ShaderUtil.checkGLError(TAG, "Texture loading");
    }
    return textureHandle[0];
  }


  // FIXME: there are LOTS of unnecessary float array inits. optimize!
  private Obj createNewObjWithNormals(Obj obj) {
    // Make sure we deal with triangles only
    //obj = ObjUtils.triangulate(obj);

    ArrayList<float[]> normalArrayList = new ArrayList<>(obj.getNumVertices());
    Map<FloatTuple, Integer> vertexToNormalIndexMap = new HashMap<>(obj.getNumVertices());
    Map<ObjFace, ArrayList<Integer>> faceToNormalIndexMap = new HashMap<>(obj.getNumFaces());

    for (int i = 0; i < obj.getNumFaces(); i++) {
      ObjFace face = obj.getFace(i);
      // Need at least 3 vertices to calculate face normal
      FloatTuple[] faceVertices = new FloatTuple[3];
      for (int j = 0; j < face.getNumVertices() && j < 3; j++) {
        FloatTuple vertex = obj.getVertex(face.getVertexIndex(j));
        faceVertices[j] = vertex;
      }
      float[] vertexNormal = calculateVertexNormal(faceVertices);

      faceToNormalIndexMap.put(face, new ArrayList<Integer>(3));
      for (FloatTuple faceVertex : faceVertices) {
        if (vertexToNormalIndexMap.containsKey(faceVertex)) {
          Integer index = vertexToNormalIndexMap.get(faceVertex);
          addFloatArray(normalArrayList.get(index), vertexNormal);
        } else {
          normalArrayList.add(vertexNormal);
          vertexToNormalIndexMap.put(faceVertex, normalArrayList.size() - 1);
        }
        Integer normalIndex = vertexToNormalIndexMap.get(faceVertex);
        faceToNormalIndexMap.get(face).add(normalIndex);
      }
    }

    // Normalize vertex normals after all faces have been evaluated
    for (float[] vertexNormal : normalArrayList) {
      normalize(vertexNormal);
    }

    // Clone to output obj
    Obj output = Objs.create();
    output.setMtlFileNames(obj.getMtlFileNames());
    // copy vertices
    for (int i = 0; i < obj.getNumVertices(); i++) {
      output.addVertex(obj.getVertex(i));
    }
    // copy texture coordinates
    for (int i = 0; i < obj.getNumTexCoords(); i++) {
      output.addTexCoord(obj.getTexCoord(i));
    }
    // inject our normals
    for (float[] normal : normalArrayList) {
      output.addNormal(toFloatTuple(normal));
    }
    // copy and inject normals to face
    for (ObjFace face : faceToNormalIndexMap.keySet()) {
      int numVertices = face.getNumVertices();
      // get vertices
      int[] v = new int[numVertices]; // triangles only!
      for (int i = 0; i < numVertices; i++) {
        v[i] = face.getVertexIndex(i);
      }

      // get texture coords
      int[] vt = new int[numVertices];
      if (face.containsTexCoordIndices()) {
        for (int i = 0; i < numVertices; i++) {
          vt[i] = face.getTexCoordIndex(i);
        }
      }

      // get normal coords
      int[] vn = new int[numVertices];
      ArrayList<Integer> normalIndeces = faceToNormalIndexMap.get(face);
      for (int i = 0; i < normalIndeces.size(); i++) {
        vn[i] = normalIndeces.get(i);
      }
      output.addFace(ObjFaces.create(v, vt, vn));
    }
    return output;
  }

  private static FloatTuple toFloatTuple(float[] vector3) {
    return FloatTuples.create(vector3[0], vector3[1], vector3[2]);
  }

  private static void normalize(float[] array) {
    float magnitude = (float) Math.sqrt(array[0] * array[0] + array[1] * array[1] + array[2] * array[2]);
    array[0] = array[0] / magnitude;
    array[1] = array[1] / magnitude;
    array[2] = array[2] / magnitude;
  }

  private void addFloatArray(float[] originalArray, float[] arrayToAdd) {
    // TODO: null check?
    if (originalArray.length != arrayToAdd.length) {
      throw new IllegalArgumentException("arrays need to be of same length");
    }
    for (int i = 0; i < originalArray.length; i++) {
      originalArray[i] = originalArray[i] + arrayToAdd[i];
    }
  }

  private static float[] calculateVertexNormal(FloatTuple[] faceVertices) {
    if (faceVertices.length != 3) {
      throw new IllegalArgumentException("Need 3 face vertices to calculate normals");
    }
    float[] vector1 = createVector(faceVertices[1], faceVertices[0]);
    float[] vector2 = createVector(faceVertices[2], faceVertices[0]);
    return cross(vector1, vector2);
  }

  private static float[] cross(float[] p1, float[] p2) {
    float x = p1[1] * p2[2] - p2[1] * p1[2];
    float y = p1[2] * p2[0] - p2[2] * p1[0];
    float z = p1[0] * p2[1] - p2[0] * p1[1];
    return new float[]{x, y, z};
  }

  private static float[] createVector(FloatTuple head, FloatTuple tail) {
    return new float[]{head.getX() - tail.getX(), head.getY() - tail.getY(), head.getZ() - tail.getZ()};
  }

  private Map<String, MtlAndTexture> fetchMaterials(Obj currObj, Context context, File objDir) throws IOException {
    Map<String, MtlAndTexture> materialByNameMap = new HashMap<>();

    List<MtlAndTexture> mtlAndTextures = new ArrayList<>();
    List<String> mtlFileNames = currObj.getMtlFileNames();

    for (String mtlFileName : mtlFileNames) {
      // TODO: tidy up this fileFinder
      List<File> files = new FileFinder(mtlFileName).find(objDir);
      File mtlFile = files.get(0);
      File mtlDir = mtlFile.getParentFile();

      try (InputStream materialInputStream = context.getContentResolver().openInputStream(Uri.fromFile(mtlFile))) {
        if (materialInputStream != null) {
          List<Mtl> mtls = MtlReader.read(materialInputStream);
          for (Mtl mtl : mtls) {
            File textureFile = null;
            // TODO: can we get multiple texture files for a single material group?
            if(mtl.getMapKd() != null) {
              String textureFileLocation = mtl.getMapKd().replaceAll("\\\\", "/");
              textureFile = new File(mtlDir, textureFileLocation);
            }
            mtlAndTextures.add(new MtlAndTexture(mtl, textureFile));
          }
        }
      }
    }

    // TODO: consider different materials sharing same name (doomed anyways?)
    for (MtlAndTexture mtlAndTexture : mtlAndTextures) {
      String name = mtlAndTexture.getMtl().getName();
      materialByNameMap.put(name, mtlAndTexture);
    }
    return materialByNameMap;
  }

  /**
   * Selects the blending mode for rendering.
   *
   * @param blendMode The blending mode. Null indicates no blending (opaque rendering).
   */
  public void setBlendMode(BlendMode blendMode) {
    this.blendMode = blendMode;
    // TODO: remove this if not needed. this was used to blend shadows
  }

  /**
   * Updates the object model matrix and applies scaling.
   *
   * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
   * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
   * @see android.opengl.Matrix
   */
  public void updateModelMatrix(float[] modelMatrix, float scaleFactor, float rotateFactor) {

    if (!initialized) {
      return;
    }

    // Use a matrix to calculate the orientation of th model
    float[] orientationMatrix = new float[16];
    Matrix.setIdentityM(orientationMatrix, 0);

    Bounds bounds = getDatasetBounds();
    if (bounds.isValid()) {
      float[] datasetCenter = datasetBounds.getCenter();

      // Rotate the model -90 degrees around the original x-axis
      Matrix.rotateM(orientationMatrix, 0, -90, 1, 0, 0);

      // Rotate the model based on the rotateFactor around the original z-axis
      Matrix.rotateM(orientationMatrix, 0, -rotateFactor, 0, 0, 1);

      // Scale the model based on the scaleFactor, but also include an initial 25cm bound on each
      // side of the 3 axes.
      final float maxLength = 0.25f * scaleFactor;
      float[] boundSize = bounds.getSize();
      float maxSize = Math.max(boundSize[0], Math.max(boundSize[1], boundSize[2]));
      Matrix.scaleM(orientationMatrix, 0,maxLength / maxSize, maxLength / maxSize, maxLength / maxSize);

      // Translate the model so that the model is centered on the xy plane and on the anchor point
      Matrix.translateM(orientationMatrix, 0, -datasetCenter[0], -datasetCenter[1], -bounds.getMinZ());

      // Calculate the final model matrix by multiplying the anchor matrix A with the orientation
      // matrix B to get AB.
      Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, orientationMatrix, 0);
    }
  }

  /**
   * Draws the model.
   *
   * @param cameraView A 4x4 view matrix, in column-major order.
   * @param cameraPerspective A 4x4 projection matrix, in column-major order.
   * @param lightIntensity Illumination intensity. Combined with diffuse and specular material
   *     properties.
   * @see #setBlendMode(BlendMode)
   * @see #updateModelMatrix(float[], float)
   * @see #setMaterialProperties(Mtl)
   * @see android.opengl.Matrix
   */
  public void draw(float[] cameraView, float[] cameraPerspective, float[] colorCorrectionRgba) {

    if (!initialized) {
      return;
    }

    ShaderUtil.checkGLError(TAG, "Before draw");

    // Build the ModelView and ModelViewProjection matrices
    // for calculating object position and light.
    Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

    GLES20.glUseProgram(program);

    // Set the lighting environment properties.
    Matrix.multiplyMV(viewLightDirection, 0, modelViewMatrix, 0, LIGHT_DIRECTION, 0);
    normalizeVec3(viewLightDirection);
    GLES20.glUniform4f(
        lightingParametersUniform,
        viewLightDirection[0],
        viewLightDirection[1],
        viewLightDirection[2],
        1.f);

    GLES20.glUniform4f(
        colorCorrectionParameterUniform,
        colorCorrectionRgba[0],
        colorCorrectionRgba[1],
        colorCorrectionRgba[2],
        colorCorrectionRgba[3]);


    for (ObjProperty objProperty : objProperties) {
      for (ObjProperty.MaterialProperty materialProperty : objProperty.materialProperties) {

        if (materialProperty.hasTexture) {
          // Attach the object texture.
          GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
          GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, materialProperty.textureId);
          GLES20.glUniform1i(textureUniform, 0);
        } else {
          // Set the object material properties.
          GLES20.glUniform3f(materialAmbientUniform, materialProperty.ambient.getX(), materialProperty.ambient.getY(), materialProperty.ambient.getZ());
          GLES20.glUniform3f(materialDiffuseUniform, materialProperty.diffuse.getX(), materialProperty.diffuse.getY(), materialProperty.diffuse.getZ());
          GLES20.glUniform3f(materialSpecularUniform, materialProperty.specular.getX(), materialProperty.specular.getY(), materialProperty.specular.getZ());
          GLES20.glUniform1f(materialShininessUniform, materialProperty.shininess);
//          Random random = new Random();
//          GLES20.glUniform3f(materialAmbientUniform, (float) Math.sin(random.nextDouble()), (float) Math.sin(random.nextDouble()), (float) Math.sin(random.nextDouble()));
//          GLES20.glUniform3f(materialDiffuseUniform, (float) Math.sin(random.nextDouble()), (float) Math.sin(random.nextDouble()), (float) Math.sin(random.nextDouble()));
//          GLES20.glUniform3f(materialSpecularUniform, (float) Math.sin(random.nextDouble()), (float) Math.sin(random.nextDouble()), (float) Math.sin(random.nextDouble()));
//          GLES20.glUniform1f(materialShininessUniform, random.nextInt(128));
        }

        // Set the vertex attributes.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, materialProperty.vertexBufferId);
        if (materialProperty.numVertices > 0) {
          GLES20.glVertexAttribPointer(
                  positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, materialProperty.verticesBaseAddress);
        }
        if (materialProperty.numNormals > 0) {
          GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, materialProperty.normalsBaseAddress);
        }
        if (materialProperty.numTexCoords > 0) {
          GLES20.glVertexAttribPointer(
                  texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, materialProperty.texCoordsBaseAddress);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);

        // Enable vertex arrays
        if (materialProperty.numVertices > 0) {
          GLES20.glEnableVertexAttribArray(positionAttribute);
        }
        if (materialProperty.numNormals > 0) {
          GLES20.glEnableVertexAttribArray(normalAttribute);
        }
        if (materialProperty.numTexCoords > 0) {
          GLES20.glEnableVertexAttribArray(texCoordAttribute);
        }

        if (blendMode != null) {
          GLES20.glDepthMask(false);
          GLES20.glEnable(GLES20.GL_BLEND);
          switch (blendMode) {
            case Shadow:
              // Multiplicative blending function for Shadow.
              GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA);
              break;
            case Grid:
              // Grid, additive blending function.
              GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
              break;
          }
        }

        if (materialProperty.indexCount > 0) {
          GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, materialProperty.indexBufferId);
          GLES20.glDrawElements(GLES20.GL_TRIANGLES, materialProperty.indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
          GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        if (blendMode != null) {
          GLES20.glDisable(GLES20.GL_BLEND);
          GLES20.glDepthMask(true);
        }

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(normalAttribute);
        GLES20.glDisableVertexAttribArray(texCoordAttribute);

        if (materialProperty.hasTexture) {
          GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
      }
    }

    ShaderUtil.checkGLError(TAG, "After draw");
  }


  private static void normalizeVec3(float[] v) {
    float reciprocalLength = 1.0f / (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    v[0] *= reciprocalLength;
    v[1] *= reciprocalLength;
    v[2] *= reciprocalLength;
  }

  private class MtlAndTexture {
    private final Mtl mtl;

    private final File textureFile;

    private Mtl getMtl() {
      return mtl;
    }

    private File getTextureFile() {
      return textureFile;
    }

    private MtlAndTexture(Mtl mtl, File textureFile) {
      this.mtl = mtl;
      this.textureFile = textureFile;
    }
  }
}
