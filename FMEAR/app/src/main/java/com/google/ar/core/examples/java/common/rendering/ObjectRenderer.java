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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  // Shader names.
  private static final String VERTEX_SHADER_NAME = "shaders/object.vert";
  private static final String FRAGMENT_SHADER_NAME = "shaders/object.frag";

  private static final int COORDS_PER_VERTEX = 3;

  // Note: the last component must be zero to avoid applying the translational part of the matrix.
  private static final float[] LIGHT_DIRECTION = new float[] {0.250f, 0.866f, 0.433f, 0.0f};
  private final float[] viewLightDirection = new float[4];

  // Object vertex buffer variables.
  private int vertexBufferId;
  private int verticesBaseAddress;
  private int texCoordsBaseAddress;
  private int normalsBaseAddress;
  private int indexBufferId;
  private int indexCount;

  private int program;
  private final int[] textures = new int[1];

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
  private int materialParametersUniform;

  // Shader location: color correction property
  private int colorCorrectionParameterUniform;

  private BlendMode blendMode = null;

  // Temporary matrices allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];

  // Set some default material properties to use for lighting.
  private float ambient = 0.3f;
  private float diffuse = 1.0f;
  private float specular = 1.0f;
  private float specularPower = 6.0f;

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
    materialParametersUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters");
    colorCorrectionParameterUniform =
            GLES20.glGetUniformLocation(program, "u_ColorCorrectionParameters");

    ShaderUtil.checkGLError(TAG, "Program parameters");

    Matrix.setIdentityM(modelMatrix, 0);

  }

  // This function updates the geometry information in the context.
  public void loadObj(Context context, File objFile)
          throws IOException {
    // TODO: Temporarily using this boolean to decide whether we want to draw or not.
    initialized = true;

    // Read the obj file.
    try (InputStream objInputStream = context.getContentResolver().openInputStream(Uri.fromFile(objFile))) {
      if (objInputStream == null) {
        // nothing to load, move on
        return;
      }
      Obj obj = ObjReader.read(objInputStream);
      Map<String, MtlAndTexture> materialsByName = fetchMaterials(obj, context, objFile.getParentFile());

      int numMaterialGroups = obj.getNumMaterialGroups();
      if (numMaterialGroups > 1) {
        Map<String, Obj> materialToObjMap = ObjSplitting.splitByMaterialGroups(obj);

        for (Map.Entry<String, Obj> entry : materialToObjMap.entrySet()) {
          String materialName = entry.getKey();
          MtlAndTexture mtlAndTexture = materialsByName.get(materialName);
          Mtl mtl = mtlAndTexture.getMtl();
          File textureFile = mtlAndTexture.getTextureFile();

          textures[0] = loadTexture(context, textureFile);
          renderObj(entry.getValue(), mtl);
        }
      } else if (numMaterialGroups == 1) {
        String materialName = obj.getMaterialGroup(0).getName();
        MtlAndTexture mtlAndTexture = materialsByName.get(materialName);
        textures[0] = loadTexture(context, mtlAndTexture.getTextureFile());
        renderObj(obj, mtlAndTexture.getMtl());
      } else {
        renderObj(obj, null);
      }
    }
  }

  private int loadTexture(Context context, File textureFile) throws IOException {
    final int[] textureHandle = new int[1];
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

  private void renderObj(Obj obj, Mtl mtl) {
    if (obj.getNumNormals() <= 0) {
      obj = createNewObjWithNormals(obj);
    }

    // Prepare the Obj so that its structure is suitable for
    // rendering with OpenGL:
    // 1. Triangulate it
    // 2. Make sure that texture coordinates are not ambiguous
    // 3. Make sure that normals are not ambiguous
    // 4. Convert it to single-indexed data
    obj = ObjUtils.convertToRenderable(obj);

    // OpenGL does not use Java arrays. ByteBuffers are used instead to provide data in a format
    // that OpenGL understands.

    // Obtain the data from the OBJ, as direct buffers:
    IntBuffer wideIndices = ObjData.getFaceVertexIndices(obj, 3);
    FloatBuffer vertices = ObjData.getVertices(obj);
    FloatBuffer texCoords = ObjData.getTexCoords(obj, 2);
    FloatBuffer normals = ObjData.getNormals(obj);

    // Convert int indices to shorts for GL ES 2.0 compatibility
    ShortBuffer indices =
        ByteBuffer.allocateDirect(2 * wideIndices.limit())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
    while (wideIndices.hasRemaining()) {
      indices.put((short) wideIndices.get());
    }
    indices.rewind();

    int[] buffers = new int[2];
    GLES20.glGenBuffers(2, buffers, 0);
    vertexBufferId = buffers[0];
    indexBufferId = buffers[1];

    // Load vertex buffer
    verticesBaseAddress = 0;
    texCoordsBaseAddress = verticesBaseAddress + 4 * vertices.limit();
    normalsBaseAddress = texCoordsBaseAddress + 4 * texCoords.limit();
    final int totalBytes = normalsBaseAddress + 4 * normals.limit();

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW);
    GLES20.glBufferSubData(
        GLES20.GL_ARRAY_BUFFER, verticesBaseAddress, 4 * vertices.limit(), vertices);
    GLES20.glBufferSubData(
        GLES20.GL_ARRAY_BUFFER, texCoordsBaseAddress, 4 * texCoords.limit(), texCoords);
    GLES20.glBufferSubData(
        GLES20.GL_ARRAY_BUFFER, normalsBaseAddress, 4 * normals.limit(), normals);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    // Load index buffer
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    indexCount = indices.limit();
    GLES20.glBufferData(
        GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * indexCount, indices, GLES20.GL_STATIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

    ShaderUtil.checkGLError(TAG, "OBJ buffer load");

    setMaterialProperties(mtl);
  }

  // FIXME: there are LOTS of unnecessary float array inits. optimize!
  private Obj createNewObjWithNormals(Obj obj) {
    // Make sure we deal with triangles only
    obj = ObjUtils.triangulate(obj);

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

  private Map<String, MtlAndTexture> fetchMaterials(Obj obj, Context context, File objDir) throws IOException {
    Map<String, MtlAndTexture> materialByNameMap = new HashMap<>();

    List<MtlAndTexture> mtlAndTextures = new ArrayList<>();
    List<String> mtlFileNames = obj.getMtlFileNames();

    for (String mtlFileName : mtlFileNames) {
      // TODO: tidy up this fileFinder
      List<File> files = new FileFinder(mtlFileName).find(objDir);
      File mtlFile = files.get(0);
      File mtlDir = mtlFile.getParentFile();

      try (InputStream materialInputStream = context.getContentResolver().openInputStream(Uri.fromFile(mtlFile))) {
        if (materialInputStream != null) {
          List<Mtl> mtls = MtlReader.read(materialInputStream);
          for (Mtl mtl : mtls) {
            // TODO: can we get multiple texture files for a single material group?
            String textureFileLocation = mtl.getMapKd().replaceAll("\\\\", "/");
            File textureFile = new File(mtlDir, textureFileLocation);
            if (textureFile.exists()) {
              mtlAndTextures.add(new MtlAndTexture(mtl, textureFile));
            } else {
              // TODO: properly handle materials without texture files
              mtlAndTextures.add(new MtlAndTexture(mtl, null));
            }
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
  public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {

    if (!initialized) {
      return;
    }

    float[] scaleMatrix = new float[16];
    Matrix.setIdentityM(scaleMatrix, 0);
    scaleMatrix[0] = scaleFactor;
    scaleMatrix[5] = scaleFactor;
    scaleMatrix[10] = scaleFactor;
    Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
  }

  /**
   * Sets the surface characteristics of the rendered model.
   *
   * @param material
   */
  public void setMaterialProperties(Mtl material) {
    if (material == null) {
      return;
    }
    // TODO: how to convert these Float Tuples into single floats ? 7
//    this.ambient = material.getKa();
//    this.diffuse = material.getKd();
//    this.specular = material.getKs();
    // this.specular = material.getNs()
    this.ambient = 0.5f;
    this.diffuse = 0.5f;
    this.specular = 0.5f;
    this.specularPower = material.getNs();
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

    // Set the object material properties.
    GLES20.glUniform4f(materialParametersUniform, ambient, diffuse, specular, specularPower);

    // Attach the object texture.
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
    GLES20.glUniform1i(textureUniform, 0);

    // Set the vertex attributes.
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);

    GLES20.glVertexAttribPointer(
        positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, verticesBaseAddress);
    GLES20.glVertexAttribPointer(normalAttribute, 3, GLES20.GL_FLOAT, false, 0, normalsBaseAddress);
    GLES20.glVertexAttribPointer(
        texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, texCoordsBaseAddress);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    // Set the ModelViewProjection matrix in the shader.
    GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(positionAttribute);
    GLES20.glEnableVertexAttribArray(normalAttribute);
    GLES20.glEnableVertexAttribArray(texCoordAttribute);

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

    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
    GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

    if (blendMode != null) {
      GLES20.glDisable(GLES20.GL_BLEND);
      GLES20.glDepthMask(true);
    }

    // Disable vertex arrays
    GLES20.glDisableVertexAttribArray(positionAttribute);
    GLES20.glDisableVertexAttribArray(normalAttribute);
    GLES20.glDisableVertexAttribArray(texCoordAttribute);

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

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
