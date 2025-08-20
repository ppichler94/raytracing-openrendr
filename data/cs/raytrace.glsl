#version 430
layout(local_size_x = 1, local_size_y = 1) in;

uniform vec3[4] cameraParams; // [center, pixel00, deltaU, deltaV]
uniform writeonly image2D outputImg;

struct Ray {
  vec3 pos;
  vec3 dir;
  vec3 invDir;
  vec3 transmittance;
  int bounceCount;
};

struct HitInfo {
    bool didHit;
    float distance;
    vec3 hitPoint;
    vec3 normal;
};

HitInfo RaySphere(Ray ray, vec3 sphereCenter, float sphereRadius) {
    HitInfo hitInfo;
    hitInfo.didHit = false;
    vec3 offsetRayOrigin = ray.pos - sphereCenter;

    float a = dot(ray.dir, ray.dir);
    float b = 2.0 * dot(offsetRayOrigin, ray.dir);
    float c = dot(offsetRayOrigin, offsetRayOrigin) - sphereRadius * sphereRadius;
    float discriminant = b * b - 4.0 * a * c;

    if (discriminant >= 0) {
        float dst = (-b - sqrt(discriminant)) / (2.0 * a);

        if (dst >= 0) {
            hitInfo.didHit = true;
            hitInfo.distance = dst;
            hitInfo.hitPoint = ray.pos + ray.dir * dst;
            hitInfo.normal = normalize(hitInfo.hitPoint - sphereCenter);
        }
    }

    return hitInfo;
}

vec3 Trace(Ray initialRay, inout uint rngState) {
    vec3 totalLight = vec3(0.0);

    const int StackCapacity = 16;
    const int MaxBounceCount = 9;
    int stackCount = 0;
    Ray stack[StackCapacity];
    stack[stackCount++] = initialRay;

    //todo: test code

    HitInfo info = RaySphere(initialRay, vec3(0, 0, -1), 0.5);
    if (info.didHit) {
        return vec3(1.0, 0.0, 0.0);
    }

    // end -------------

    while (stackCount > 0) {
        Ray ray = stack[--stackCount];
        for (int i = ray.bounceCount; i <= MaxBounceCount; i++) {

        }
    }

    return totalLight;
}

void main() {
    ivec2 coords = ivec2(gl_GlobalInvocationID.xy);

    vec3 cameraCenter = cameraParams[0];
    vec3 pixel00 = cameraParams[1];
    vec3 deltaU = cameraParams[2];
    vec3 deltaV = cameraParams[3];

    vec3 pixelCenter = pixel00 + (coords.x * deltaU) + (coords.y * deltaV);
    vec3 direction = normalize(pixelCenter - cameraCenter);

    Ray ray = Ray(cameraCenter, direction, direction * -1.0, vec3(1), 0);

    uint rngState = 0;

    vec3 color = Trace(ray, rngState);
    imageStore(outputImg, coords, vec4(color.xyz, 1.0));
}